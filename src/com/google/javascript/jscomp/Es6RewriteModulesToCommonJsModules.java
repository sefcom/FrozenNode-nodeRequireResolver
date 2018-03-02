/*
 * Copyright 2018 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.deps.ModuleLoader.ModulePath;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.Nullable;

/**
 * Rewrites an ES6 module to a CommonJS-like module for the sake of per-file transpilation +
 * bunlding (e.g. Closure Bundler). Output is not meant to be type checked.
 */
public class Es6RewriteModulesToCommonJsModules implements CompilerPass {
  private static final String JSCOMP_DEFAULT_EXPORT = "$$default";
  private static final String MODULE = "$$module";
  private static final String EXPORTS = "$$exports";
  private static final String REQUIRE = "$$require";

  private final AbstractCompiler compiler;

  public Es6RewriteModulesToCommonJsModules(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    for (Node script : root.children()) {
      if (Es6RewriteModules.isEs6ModuleRoot(script)) {
        NodeTraversal.traverseEs6(compiler, script, new Rewriter(compiler, script));
      }
    }
  }

  /**
   * Rewrites a single ES6 module into a CommonJS like module designed to be loaded in the
   * compiler's module runtime.
   */
  private static class Rewriter extends AbstractPostOrderCallback {
    private Node requireInsertSpot;
    private final Node script;
    private final Map<String, String> exportedNameToLocalQName;
    private final Set<Node> imports;
    private final Set<String> importRequests;
    private final AbstractCompiler compiler;
    private final ModulePath modulePath;

    Rewriter(AbstractCompiler compiler, Node script) {
      this.compiler = compiler;
      this.script = script;
      requireInsertSpot = null;
      // TreeMap because ES6 orders the export key using natural ordering.
      exportedNameToLocalQName = new TreeMap<>();
      importRequests = new LinkedHashSet<>();
      imports = new HashSet<>();
      modulePath = compiler.getInput(script.getInputId()).getPath();
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case IMPORT:
          visitImport(n);
          break;
        case EXPORT:
          visitExport(t, n, parent);
          break;
        case SCRIPT:
          visitScript(t, n);
          break;
        case NAME:
          maybeRenameImportedValue(t, n);
          break;
        default:
          break;
      }
    }

    /**
     * Given an import node gets the name of the var to use for the imported module.
     *
     * Example:
     *   import {v} from './foo.js'; use(v);
     * Can become:
     *   const module$foo = require('./foo.js'); use(module$foo.v);
     * This method would return "module$foo".
     */
    private String getVarNameOfImport(Node importDecl) {
      checkState(importDecl.isImport());
      return getVarNameOfImport(importDecl.getLastChild().getString());
    }

    private String getVarNameOfImport(String importRequest) {
      return modulePath.resolveModuleAsPath(importRequest).toModuleName();
    }

    /**
     * @return qualified name to use to reference an imported value.
     *     <p>Examples:
     *     <ul>
     *       <li>If referencing an import spec like v in "import {v} from './foo.js'" then this
     *           would return "module$foo.v".
     *       <li>If referencing an import star like m in "import * as m from './foo.js'" then this
     *           would return "module$foo".
     *       <li>If referencing an import default like d in "import d from './foo.js'" then this
     *           would return "module$foo.default".
     *
     * Used to rename references to imported values within this module.
     */
    private String getNameOfImportedValue(Node nameNode) {
      Node importDecl = nameNode;

      while (!importDecl.isImport()) {
        importDecl = importDecl.getParent();
      }

      String moduleName = getVarNameOfImport(importDecl);

      if (nameNode.getParent().isImportSpec()) {
        return moduleName + "." + nameNode.getParent().getFirstChild().getString();
      } else if (nameNode.isImportStar()) {
        return moduleName;
      } else {
        checkState(nameNode.getParent().isImport());
        return moduleName + ".default";
      }
    }

    /**
     * @param nameNode any variable name that is potentially from an import statement
     * @return qualified name to use to reference an imported value if the given node is an imported
     *     name or null if the value is not imported or if it is in the import statement itself
     */
    @Nullable
    private String maybeGetNameOfImportedValue(Scope s, Node nameNode) {
      checkState(nameNode.isName());
      Var var = s.getVar(nameNode.getString());

      if (var != null
          // variables added implicitly to the scope, like arguments, have a null name node
          && var.getNameNode() != null
          && NodeUtil.isImportedName(var.getNameNode())
          && nameNode != var.getNameNode()) {
        return getNameOfImportedValue(var.getNameNode());
      }

      return null;
    }

    /**
     * Renames the given name node if it is an imported value.
     */
    private void maybeRenameImportedValue(NodeTraversal t, Node n) {
      checkState(n.isName());
      Node parent = n.getParent();

      if (parent.isExport()
          || parent.isExportSpec()
          || parent.isImport()
          || parent.isImportSpec()) {
        return;
      }

      String qName = maybeGetNameOfImportedValue(t.getScope(), n);

      if (qName != null) {
        n.replaceWith(NodeUtil.newQName(compiler, qName));
        t.reportCodeChange();
      }
    }

    private void visitScript(NodeTraversal t, Node script) {
      checkState(this.script == script);
      Node moduleNode = script.getFirstChild();
      checkState(moduleNode.isModuleBody());
      moduleNode.detach();
      script.addChildrenToFront(moduleNode.removeChildren());

      // Order here is important. We want the end result to be:
      //  $jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {
      //   // First to ensure circular deps can see exports of this module before we require them,
      //   // and also so that temporal deadzone is respected.
      //   //<export def>
      //   // Second so the module definition can reference imported modules, and so any require'd
      //   // modules are loaded.
      //   //<requires>
      //   // And finally last is the actual module definition.
      //   //<module def>
      //  }, /* <module path> */, [/* <deps> */]);
      // As a result the calls below are in *inverse* order to what we want above so they can keep
      // adding to the front of the script.
      addRequireCalls();
      addExportDef();
      registerAndLoadModule(t);
    }

    /** Adds one call to require per imported module. */
    private void addRequireCalls() {
      if (!importRequests.isEmpty()) {
        for (Node importDecl : imports) {
          importDecl.detach();
        }

        Set<String> importedNames = new HashSet<>();

        for (String request : importRequests) {
          String varName = getVarNameOfImport(request);
          if (importedNames.add(varName)) {
            Node requireCall = IR.call(IR.name(REQUIRE), IR.string(request));
            requireCall.putBooleanProp(Node.FREE_CALL, true);
            Node var = IR.var(IR.name(varName), requireCall);
            var.useSourceInfoIfMissingFromForTree(script);
            script.addChildAfter(var, requireInsertSpot);
            requireInsertSpot = var;
          }
        }
      }
    }

    /**
     * Wraps the entire current module definition in a $jscomp.registerAndLoadModule function.
     */
    private void registerAndLoadModule(NodeTraversal t) {
      Node block = IR.block();
      block.addChildrenToFront(script.removeChildren());

      Node moduleFunction =
          IR.function(
              IR.name(""),
              IR.paramList(IR.name(REQUIRE), IR.name(EXPORTS), IR.name(MODULE)),
              block);

      Node shallowDeps = new Node(Token.ARRAYLIT);

      for (String request : importRequests) {
        shallowDeps.addChildToBack(IR.string(request));
      }

      Node exprResult =
          IR.exprResult(
              IR.call(
                  IR.getprop(IR.name("$jscomp"), IR.string("registerAndLoadModule")),
                  moduleFunction,
                  // Specifically use the input's name rather than modulePath.toString(). The former
                  // is the raw path and the latter is encoded (special characters are replaced).
                  // This is designed to run in a web browser and we want to preserve the URL given
                  // to us. But the encodings will replace : with - due to windows.
                  IR.string(t.getInput().getName()),
                  shallowDeps));

      script.addChildToBack(exprResult.useSourceInfoIfMissingFromForTree(script));

      compiler.reportChangeToChangeScope(script);
      compiler.reportChangeToChangeScope(moduleFunction);
      t.reportCodeChange();
    }

    /** Adds exports to the exports object using Object.defineProperties. */
    private void addExportDef() {
      if (!exportedNameToLocalQName.isEmpty()) {
        Node definePropertiesLit = IR.objectlit();

        for (Map.Entry<String, String> entry : exportedNameToLocalQName.entrySet()) {
          addExport(definePropertiesLit, entry.getKey(), entry.getValue());
        }

        script.addChildToFront(
            IR.exprResult(
                    IR.call(
                        NodeUtil.newQName(compiler, "Object.defineProperties"),
                        IR.name(EXPORTS),
                        definePropertiesLit))
                .useSourceInfoIfMissingFromForTree(script));
      }
    }

    /** Adds an ES5 getter to the given object literal to use an an export. */
    private void addExport(Node definePropertiesLit, String exportedName, String localQName) {
      Node exportedValue = NodeUtil.newQName(compiler, localQName);
      Node getterFunction =
          IR.function(IR.name(""), IR.paramList(), IR.block(IR.returnNode(exportedValue)));

      Node objLit =
          IR.objectlit(
              IR.stringKey("enumerable", IR.trueNode()), IR.stringKey("get", getterFunction));
      definePropertiesLit.addChildToBack(IR.stringKey(exportedName, objLit));

      compiler.reportChangeToChangeScope(getterFunction);
    }

    private void visitImport(Node importDecl) {
      importRequests.add(importDecl.getLastChild().getString());
      imports.add(importDecl);
    }

    private void visitExportDefault(NodeTraversal t, Node export, Node parent) {
      Node child = export.getFirstChild();
      String name = null;

      if (child.isFunction() || child.isClass()) {
        name = NodeUtil.getName(child);
      }

      if (name != null) {
        Node decl = child.detach();
        parent.replaceChild(export, decl);
      } else {
        name = JSCOMP_DEFAULT_EXPORT;
        // Default exports are constant in more ways than one. Not only can they not be
        // overwritten but they also act like a const for temporal dead-zone purposes.
        Node var = IR.constNode(IR.name(name), export.removeFirstChild());
        parent.replaceChild(export, var.useSourceInfoIfMissingFromForTree(export));
      }

      exportedNameToLocalQName.put("default", name);
      t.reportCodeChange();
    }

    private void visitExportFrom(NodeTraversal t, Node export, Node parent) {
      //   export {x, y as z} from 'moduleIdentifier';
      Node moduleIdentifier = export.getLastChild();
      Node importNode = IR.importNode(IR.empty(), IR.empty(), moduleIdentifier.cloneNode());
      importNode.useSourceInfoFrom(export);
      parent.addChildBefore(importNode, export);
      visit(t, importNode, parent);

      String moduleName = getVarNameOfImport(moduleIdentifier.getString());

      for (Node exportSpec : export.getFirstChild().children()) {
        exportedNameToLocalQName.put(
            exportSpec.getLastChild().getString(),
            moduleName + "." + exportSpec.getFirstChild().getString());
      }

      parent.removeChild(export);
      t.reportCodeChange();
    }

    private void visitExportSpecs(NodeTraversal t, Node export, Node parent) {
      //     export {Foo};
      for (Node exportSpec : export.getFirstChild().children()) {
        String localName = exportSpec.getFirstChild().getString();
        Var var = t.getScope().getVar(localName);
        if (var != null && NodeUtil.isImportedName(var.getNameNode())) {
          localName = maybeGetNameOfImportedValue(t.getScope(), exportSpec.getFirstChild());
          checkNotNull(localName);
        }
        exportedNameToLocalQName.put(exportSpec.getLastChild().getString(), localName);
      }
      parent.removeChild(export);
      t.reportCodeChange();
    }

    private void visitExportNameDeclaration(Node declaration) {
      //    export var Foo;
      //    export let {a, b:[c,d]} = {};
      List<Node> lhsNodes = NodeUtil.findLhsNodesInNode(declaration);

      for (Node lhs : lhsNodes) {
        checkState(lhs.isName());
        String name = lhs.getString();
        exportedNameToLocalQName.put(name, name);
      }
    }

    private void visitExportDeclaration(NodeTraversal t, Node export, Node parent) {
      //    export var Foo;
      //    export function Foo() {}
      // etc.
      Node declaration = export.getFirstChild();

      if (NodeUtil.isNameDeclaration(declaration)) {
        visitExportNameDeclaration(declaration);
      } else {
        checkState(declaration.isFunction() || declaration.isClass());
        String name = declaration.getFirstChild().getString();
        exportedNameToLocalQName.put(name, name);
      }

      parent.replaceChild(export, declaration.detach());
      t.reportCodeChange();
    }

    private void visitExport(NodeTraversal t, Node export, Node parent) {
      if (export.getBooleanProp(Node.EXPORT_DEFAULT)) {
        visitExportDefault(t, export, parent);
      } else if (export.getBooleanProp(Node.EXPORT_ALL_FROM)) {
        // TODO(johnplaisted)
        compiler.report(JSError.make(export, Es6ToEs3Util.CANNOT_CONVERT_YET, "Wildcard export"));
      } else if (export.hasTwoChildren()) {
        visitExportFrom(t, export, parent);
      } else {
        if (export.getFirstChild().getToken() == Token.EXPORT_SPECS) {
          visitExportSpecs(t, export, parent);
        } else {
          visitExportDeclaration(t, export, parent);
        }
      }
    }
  }
}
