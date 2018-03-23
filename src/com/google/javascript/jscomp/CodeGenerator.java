/*
 * Copyright 2004 The Closure Compiler Authors.
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

import com.google.common.base.Preconditions;
import com.google.debugging.sourcemap.Util;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;
import java.util.HashMap;
import java.util.Map;

// JAMES (ME)
import java.io.File;import java.io.InputStream;
import java.io.InputStreamReader;import java.io.BufferedReader;import java.io.FileReader;
import java.util.List;import java.util.ArrayList;
import java.io.BufferedWriter;import java.io.FileWriter;import java.io.OutputStreamWriter;
import java.io.PrintWriter;

/**
 * CodeGenerator generates codes from a parse tree, sending it to the specified
 * CodeConsumer.
 *
 */
public class CodeGenerator {
  private static final String LT_ESCAPED = "\\x3c";
  private static final String GT_ESCAPED = "\\x3e";

  // A memoizer for formatting strings as JS strings.
  private final Map<String, String> escapedJsStrings = new HashMap<>();

  private final CodeConsumer cc;

  private final OutputCharsetEncoder outputCharsetEncoder;

  private final boolean preferSingleQuotes;
  private final boolean preserveTypeAnnotations;
  private final boolean trustedStrings;
  private final boolean quoteKeywordProperties;
  private final boolean useOriginalName;
  private final JSDocInfoPrinter jsDocInfoPrinter;

  private CodeGenerator(CodeConsumer consumer) {
    cc = consumer;
    outputCharsetEncoder = null;
    preferSingleQuotes = false;
    trustedStrings = true;
    preserveTypeAnnotations = false;
    quoteKeywordProperties = false;
    useOriginalName = false;
    this.jsDocInfoPrinter = new JSDocInfoPrinter(false);
  }

  protected CodeGenerator(CodeConsumer consumer, CompilerOptions options) {
    cc = consumer;
    initRequireResolver(options); // JAMES

    this.outputCharsetEncoder = new OutputCharsetEncoder(options.getOutputCharset());
    this.preferSingleQuotes = options.preferSingleQuotes;
    this.trustedStrings = options.trustedStrings;
    this.preserveTypeAnnotations = options.preserveTypeAnnotations;
    this.quoteKeywordProperties = options.shouldQuoteKeywordProperties();
    this.useOriginalName = options.getUseOriginalNamesInOutput();
    this.jsDocInfoPrinter = new JSDocInfoPrinter(useOriginalName);
  }

  static CodeGenerator forCostEstimation(CodeConsumer consumer) {
    return new CodeGenerator(consumer);
  }

  /** Insert a top-level identifying file as .i.js generated typing file. */
  void tagAsTypeSummary() {
    add("/** @fileoverview @typeSummary */\n");
  }

  /** Insert a top-level @externs comment. */
  public void tagAsExterns() {
    add("/** @externs */\n");
  }

  /**
   * Insert a ECMASCRIPT 5 strict annotation.
   */
  public void tagAsStrict() {
    add("'use strict';");
    cc.endLine();
  }

  protected void add(String str) {
    cc.add(str);
  }

  protected void add(Node n) {
    add(n, Context.OTHER);
  }

  protected void add(Node n, Context context) {
    if (!cc.continueProcessing()) {
      return;
    }

    if (preserveTypeAnnotations && n.getJSDocInfo() != null) {
      String jsdocAsString = jsDocInfoPrinter.print(n.getJSDocInfo());
      // Don't print an empty jsdoc
      if (!jsdocAsString.equals("/** */ ")) {
        add(jsdocAsString);
      }
    }

    Token type = n.getToken();
    String opstr = NodeUtil.opToStr(type);
    int childCount = n.getChildCount();
    Node first = n.getFirstChild();
    Node last = n.getLastChild();

    // Handle all binary operators
    if (opstr != null && first != last) {
      Preconditions.checkState(
          childCount == 2,
          "Bad binary operator \"%s\": expected 2 arguments but got %s",
          opstr,
          childCount);
      int p = precedence(n);

      // For right-hand-side of operations, only pass context if it's
      // the IN_FOR_INIT_CLAUSE one.
      Context rhsContext = getContextForNoInOperator(context);

      boolean needsParens = (context == Context.START_OF_EXPR) && first.isObjectPattern();
      if (n.isAssign() && needsParens) {
        add("(");
      }

      if (NodeUtil.isAssignmentOp(n) || type == Token.EXPONENT) {
        // Assignment operators and '**' are the only right-associative binary operators
        addExpr(first, p + 1, context);
        cc.addOp(opstr, true);
        addExpr(last, p, rhsContext);
      } else {
        unrollBinaryOperator(n, type, opstr, context, rhsContext, p, p + 1);
      }

      if (n.isAssign() && needsParens) {
        add(")");
      }
      return;
    }

    cc.startSourceMapping(n);

    switch (type) {
      case TRY:
        {
          checkState(first.getNext().isNormalBlock() && !first.getNext().hasMoreThanOneChild());
          checkState(childCount >= 2 && childCount <= 3);

          add("try");
          add(first);

          // second child contains the catch block, or nothing if there
          // isn't a catch block
          Node catchblock = first.getNext().getFirstChild();
          if (catchblock != null) {
            add(catchblock);
          }

          if (childCount == 3) {
            cc.maybeInsertSpace();
            add("finally");
            add(last);
          }
          break;
        }

      case CATCH:
        Preconditions.checkState(childCount == 2, n);
        cc.maybeInsertSpace();
        add("catch");
        cc.maybeInsertSpace();
        add("(");
        add(first);
        add(")");
        add(last);
        break;

      case THROW:
        Preconditions.checkState(childCount == 1, n);
        add("throw");
        cc.maybeInsertSpace();
        add(first);

        // Must have a ';' after a throw statement, otherwise safari can't
        // parse this.
        cc.endStatement(true);
        break;

      case RETURN:
        add("return");
        if (childCount == 1) {
          cc.maybeInsertSpace();
          if (preserveTypeAnnotations && first.getJSDocInfo() != null) {
            add("(");
            add(first);
            add(")");
          } else {
            add(first);
          }
        } else {
          checkState(childCount == 0, n);
        }
        cc.endStatement();
        break;

      case VAR:
        add("var ");
        addList(first, false, getContextForNoInOperator(context), ",");
        if (n.getParent() == null || NodeUtil.isStatement(n)) {
          cc.endStatement();
        }
        break;

      case CONST:
        add("const ");
        addList(first, false, getContextForNoInOperator(context), ",");
        if (n.getParent() == null || NodeUtil.isStatement(n)) {
          cc.endStatement();
        }
        break;

      case LET:
        add("let ");
        addList(first, false, getContextForNoInOperator(context), ",");
        if (n.getParent() == null || NodeUtil.isStatement(n)) {
          cc.endStatement();
        }
        break;

      case LABEL_NAME:
        Preconditions.checkState(!n.getString().isEmpty(), n);
        addIdentifier(n.getString());
        break;

      case DESTRUCTURING_LHS:
        add(first);
        if (first != last) {
          checkState(childCount == 2, n);
          cc.addOp("=", true);
          add(last);
        }
        break;

      case NAME:
        if (useOriginalName && n.getOriginalName() != null) {
          addIdentifier(n.getOriginalName());
        } else {
          addIdentifier(n.getString());
        }
        maybeAddOptional(n);
        maybeAddTypeDecl(n);

        if (first != null && !first.isEmpty()) {
          checkState(childCount == 1, n);
          cc.addOp("=", true);
          if (first.isComma() || (first.isCast() && first.getFirstChild().isComma())) {
            addExpr(first, NodeUtil.precedence(Token.ASSIGN), Context.OTHER);
          } else {
            // Add expression, consider nearby code at lowest level of
            // precedence.
            addExpr(first, 0, getContextForNoInOperator(context));
          }
        }
        break;

      case ARRAYLIT:
        add("[");
        addArrayList(first);
        add("]");
        break;

      case ARRAY_PATTERN:
        add("[");
        addArrayList(first);
        add("]");
        maybeAddTypeDecl(n);
        break;

      case PARAM_LIST:
        add("(");
        addList(first);
        add(")");
        break;

      case DEFAULT_VALUE:
        add(first);
        maybeAddTypeDecl(n);
        cc.addOp("=", true);
        addExpr(first.getNext(), 1, Context.OTHER);
        break;

      case COMMA:
        Preconditions.checkState(childCount == 2, n);
        unrollBinaryOperator(
            n, Token.COMMA, ",", context, getContextForNoInOperator(context), 0, 0);
        break;

      case NUMBER:
        Preconditions.checkState(childCount == 0, n);
        cc.addNumber(n.getDouble(), n);
        break;

      case TYPEOF:
      case VOID:
      case NOT:
      case BITNOT:
      case POS:
        {
          // All of these unary operators are right-associative
          checkState(childCount == 1, n);
          cc.addOp(NodeUtil.opToStrNoFail(type), false);
          addExpr(first, NodeUtil.precedence(type), Context.OTHER);
          break;
        }

      case NEG:
        {
          checkState(childCount == 1, n);

          // It's important to our validity checker that the code we print produces the same AST as
          // the code we parse back. NEG is a weird case because Rhino parses "- -2" as "2".
          if (n.getFirstChild().isNumber()) {
            cc.addNumber(-n.getFirstChild().getDouble(), n.getFirstChild());
          } else {
            cc.addOp(NodeUtil.opToStrNoFail(type), false);
            addExpr(first, NodeUtil.precedence(type), Context.OTHER);
          }

          break;
        }

      case HOOK:
        {
          checkState(childCount == 3, n);
          int p = NodeUtil.precedence(type);
          Context rhsContext = getContextForNoInOperator(context);
          addExpr(first, p + 1, context);
          cc.addOp("?", true);
          addExpr(first.getNext(), 1, rhsContext);
          cc.addOp(":", true);
          addExpr(last, 1, rhsContext);
          break;
        }

      case REGEXP:
        if (!first.isString() || !last.isString()) {
          throw new Error("Expected children to be strings");
        }

        String regexp = regexpEscape(first.getString());

        // I only use one .add because whitespace matters
        if (childCount == 2) {
          add(regexp + last.getString());
        } else {
          checkState(childCount == 1, n);
          add(regexp);
        }
        break;

      case FUNCTION:
        {
          if (n.getClass() != Node.class) {
            throw new Error("Unexpected Node subclass.");
          }
          checkState(childCount == 3, n);
          if (n.isArrowFunction()) {
            addArrowFunction(n, first, last, context);
          } else {
            addFunction(n, first, last, context);
          }
          break;
        }
      case REST:
        add("...");
        add(first);
        maybeAddTypeDecl(n);
        break;

      case SPREAD:
        add("...");
        add(n.getFirstChild());
        break;

      case EXPORT:
        add("export");
        if (n.getBooleanProp(Node.EXPORT_DEFAULT)) {
          add("default");
        }
        if (n.getBooleanProp(Node.EXPORT_ALL_FROM)) {
          add("*");
          checkState(first != null && first.isEmpty(), n);
        } else {
          add(first);
        }
        if (childCount == 2) {
          add("from");
          add(last);
        }
        processEnd(first, context);
        break;

      case IMPORT:
        add("import");

        Node second = first.getNext();
        if (!first.isEmpty()) {
          add(first);
          if (!second.isEmpty()) {
            cc.listSeparator();
          }
        }
        if (!second.isEmpty()) {
          add(second);
        }
        if (!first.isEmpty() || !second.isEmpty()) {
          add("from");
        }
        add(last);
        cc.endStatement();
        break;

      case EXPORT_SPECS:
      case IMPORT_SPECS:
        add("{");
        for (Node c = first; c != null; c = c.getNext()) {
          if (c != first) {
            cc.listSeparator();
          }
          add(c);
        }
        add("}");
        break;

      case EXPORT_SPEC:
      case IMPORT_SPEC:
        add(first);
        if (n.isShorthandProperty() && first.getString().equals(last.getString())) {
          break;
        }
        add("as");
        add(last);
        break;

      case IMPORT_STAR:
        add("*");
        add("as");
        add(n.getString());
        break;

        // CLASS -> NAME,EXPR|EMPTY,BLOCK
      case CLASS:
        {
          checkState(childCount == 3, n);
          boolean classNeedsParens = (context == Context.START_OF_EXPR);
          if (classNeedsParens) {
            add("(");
          }

          Node name = first;
          Node superClass = first.getNext();
          Node members = last;

          add("class");
          if (!name.isEmpty()) {
            add(name);
          }

          maybeAddGenericTypes(first);

          if (!superClass.isEmpty()) {
            add("extends");
            add(superClass);
          }

          Node interfaces = (Node) n.getProp(Node.IMPLEMENTS);
          if (interfaces != null) {
            add("implements");
            Node child = interfaces.getFirstChild();
            add(child);
            while ((child = child.getNext()) != null) {
              add(",");
              cc.maybeInsertSpace();
              add(child);
            }
          }
          add(members);
          cc.endClass(context == Context.STATEMENT);

          if (classNeedsParens) {
            add(")");
          }
        }
        break;

      case CLASS_MEMBERS:
      case INTERFACE_MEMBERS:
      case NAMESPACE_ELEMENTS:
        cc.beginBlock();
        for (Node c = first; c != null; c = c.getNext()) {
          add(c);
          processEnd(c, context);
          cc.endLine();
        }
        cc.endBlock(false);
        break;
      case ENUM_MEMBERS:
        cc.beginBlock();
        for (Node c = first; c != null; c = c.getNext()) {
          add(c);
          if (c.getNext() != null) {
            add(",");
          }
          cc.endLine();
        }
        cc.endBlock(false);
        break;
      case GETTER_DEF:
      case SETTER_DEF:
      case MEMBER_FUNCTION_DEF:
      case MEMBER_VARIABLE_DEF:
        {
          checkState(
              n.getParent().isObjectLit()
                  || n.getParent().isClassMembers()
                  || n.getParent().isInterfaceMembers()
                  || n.getParent().isRecordType()
                  || n.getParent().isIndexSignature());

          maybeAddAccessibilityModifier(n);
          if (n.isStaticMember()) {
            add("static ");
          }

          if (!n.isMemberVariableDef() && n.getFirstChild().isGeneratorFunction()) {
            checkState(type == Token.MEMBER_FUNCTION_DEF, n);
            add("*");
          }

          if (n.isMemberFunctionDef() && n.getFirstChild().isAsyncFunction()) {
            add("async ");
          }

          switch (type) {
            case GETTER_DEF:
              // Get methods have no parameters.
              Preconditions.checkState(!first.getSecondChild().hasChildren(), n);
              add("get ");
              break;
            case SETTER_DEF:
              // Set methods have one parameter.
              Preconditions.checkState(first.getSecondChild().hasOneChild(), n);
              add("set ");
              break;
            case MEMBER_FUNCTION_DEF:
            case MEMBER_VARIABLE_DEF:
              // nothing to do.
              break;
            default:
              break;
          }

          // The name is on the GET or SET node.
          String name = n.getString();
          if (n.isMemberVariableDef()) {
            add(n.getString());
            maybeAddOptional(n);
            maybeAddTypeDecl(n);
          } else {
            checkState(childCount == 1, n);
            checkState(first.isFunction(), first);

            // The function referenced by the definition should always be unnamed.
            checkState(first.getFirstChild().getString().isEmpty(), first);

            Node fn = first;
            Node parameters = fn.getSecondChild();
            Node body = fn.getLastChild();

            // Add the property name.
            if (!n.isQuotedString()
                && TokenStream.isJSIdentifier(name)
                &&
                // do not encode literally any non-literal characters that were
                // Unicode escaped.
                NodeUtil.isLatin(name)) {
              add(name);
              maybeAddGenericTypes(fn.getFirstChild());
            } else {
              // Determine if the string is a simple number.
              double d = getSimpleNumber(name);
              if (!Double.isNaN(d)) {
                cc.addNumber(d, n);
              } else {
                addJsString(n);
              }
            }
            maybeAddOptional(fn);
            add(parameters);
            maybeAddTypeDecl(fn);
            add(body);
          }
          break;
        }

      case SCRIPT:
      case MODULE_BODY:
      case BLOCK:
      case ROOT:
        {
          if (n.getClass() != Node.class) {
            throw new Error("Unexpected Node subclass.");
          }
          boolean preserveBlock = n.isNormalBlock() && !n.isSyntheticBlock();
          if (preserveBlock) {
            cc.beginBlock();
          }

          boolean preferLineBreaks =
              type == Token.SCRIPT
                  || (type == Token.BLOCK && !preserveBlock && n.getParent().isScript());
          for (Node c = first; c != null; c = c.getNext()) {
            add(c, Context.STATEMENT);

            if (c.isFunction() || c.isClass()) {
              cc.maybeLineBreak();
            }

            // Prefer to break lines in between top-level statements
            // because top-level statements are more homogeneous.
            if (preferLineBreaks) {
              cc.notePreferredLineBreak();
            }
          }
          if (preserveBlock) {
            cc.endBlock(cc.breakAfterBlockFor(n, context == Context.STATEMENT));
          }
          break;
        }

      case FOR:
        Preconditions.checkState(childCount == 4, n);
        add("for");
        cc.maybeInsertSpace();
        add("(");
        if (NodeUtil.isNameDeclaration(first)) {
          add(first, Context.IN_FOR_INIT_CLAUSE);
        } else {
          addExpr(first, 0, Context.IN_FOR_INIT_CLAUSE);
        }
        add(";");
        if (!first.getNext().isEmpty()) {
          cc.maybeInsertSpace();
        }
        add(first.getNext());
        add(";");
        if (!first.getNext().getNext().isEmpty()) {
          cc.maybeInsertSpace();
        }
        add(first.getNext().getNext());
        add(")");
        addNonEmptyStatement(last, getContextForNonEmptyExpression(context), false);
        break;

      case FOR_IN:
        Preconditions.checkState(childCount == 3, n);
        add("for");
        cc.maybeInsertSpace();
        add("(");
        add(first);
        add("in");
        add(first.getNext());
        add(")");
        addNonEmptyStatement(last, getContextForNonEmptyExpression(context), false);
        break;

      case FOR_OF:
        Preconditions.checkState(childCount == 3, n);
        add("for");
        cc.maybeInsertSpace();
        add("(");
        add(first);
        cc.maybeInsertSpace();
        add("of");
        cc.maybeInsertSpace();
        add(first.getNext());
        add(")");
        addNonEmptyStatement(last, getContextForNonEmptyExpression(context), false);
        break;

      case DO:
        Preconditions.checkState(childCount == 2, n);
        add("do");
        addNonEmptyStatement(first, Context.OTHER, false);
        cc.maybeInsertSpace();
        add("while");
        cc.maybeInsertSpace();
        add("(");
        add(last);
        add(")");
        cc.endStatement();
        break;

      case WHILE:
        Preconditions.checkState(childCount == 2, n);
        add("while");
        cc.maybeInsertSpace();
        add("(");
        add(first);
        add(")");
        addNonEmptyStatement(last, getContextForNonEmptyExpression(context), false);
        break;

      case EMPTY:
        Preconditions.checkState(childCount == 0, n);
        break;

      case GETPROP:
        {
          // This attempts to convert rewritten aliased code back to the original code,
          // such as when using goog.scope(). See ScopedAliases.java for the original code.
          if (useOriginalName && n.getOriginalName() != null) {
            // The ScopedAliases pass will convert variable assignments and function declarations
            // to assignments to GETPROP nodes, like $jscomp.scope.SOME_VAR = 3;. This attempts to
            // rewrite it back to the original code.
            if (n.getFirstChild().matchesQualifiedName("$jscomp.scope")
                && n.getParent().isAssign()) {
              add("var ");
            }
            addIdentifier(n.getOriginalName());
            break;
          }
          Preconditions.checkState(
              childCount == 2, "Bad GETPROP: expected 2 children, but got %s", childCount);
          checkState(last.isString(), "Bad GETPROP: RHS should be STRING");
          boolean needsParens = (first.isNumber());
          if (needsParens) {
            add("(");
          }
          addExpr(first, NodeUtil.precedence(type), context);
          if (needsParens) {
            add(")");
          }
          if (quoteKeywordProperties && TokenStream.isKeyword(last.getString())) {
            add("[");
            add(last);
            add("]");
          } else {
            add(".");
            addIdentifier(last.getString());
          }
          break;
        }

      case GETELEM:
        Preconditions.checkState(
            childCount == 2,
            "Bad GETELEM node: Expected 2 children but got %s. For node: %s",
            childCount,
            n);
        addExpr(first, NodeUtil.precedence(type), context);
        add("[");
        add(first.getNext());
        add("]");
        break;

      case WITH:
        Preconditions.checkState(childCount == 2, n);
        add("with(");
        add(first);
        add(")");
        addNonEmptyStatement(last, getContextForNonEmptyExpression(context), false);
        break;

      case INC:
      case DEC:
        {
          checkState(childCount == 1, n);
          String o = type == Token.INC ? "++" : "--";
          boolean postProp = n.getBooleanProp(Node.INCRDECR_PROP);
          if (postProp) {
            addExpr(first, NodeUtil.precedence(type), context);
            cc.addOp(o, false);
          } else {
            cc.addOp(o, false);
            add(first);
          }
          break;
        }

      case CALL:
        // We have two special cases here:
        // 1) If the left hand side of the call is a direct reference to eval,
        // then it must have a DIRECT_EVAL annotation. If it does not, then
        // that means it was originally an indirect call to eval, and that
        // indirectness must be preserved.
        // 2) If the left hand side of the call is a property reference,
        // then the call must not a FREE_CALL annotation. If it does, then
        // that means it was originally an call without an explicit this and
        // that must be preserved.
		// START JAMES (ME)
        Node tempNode = forwardCheckNodeRequire(first);
        if(tempNode != null){
          n = tempNode;
          break;
        }
        // END JAMES ME
        if (isIndirectEval(first) || (n.getBooleanProp(Node.FREE_CALL) && NodeUtil.isGet(first))) {
          add("(0,");
          addExpr(first, NodeUtil.precedence(Token.COMMA), Context.OTHER);
          add(")");
        } else {
          addExpr(first, NodeUtil.precedence(type), context);
        }
        Node args = first.getNext();
        add("(");
        addList(args);
        add(")");
        break;

      case IF:
        Preconditions.checkState(childCount == 2 || childCount == 3, n);
        boolean hasElse = childCount == 3;
        boolean ambiguousElseClause = context == Context.BEFORE_DANGLING_ELSE && !hasElse;
        if (ambiguousElseClause) {
          cc.beginBlock();
        }

        add("if");
        cc.maybeInsertSpace();
        add("(");
        add(first);
        add(")");

        if (hasElse) {
          addNonEmptyStatement(first.getNext(), Context.BEFORE_DANGLING_ELSE, false);
          cc.maybeInsertSpace();
          add("else");
          addNonEmptyStatement(last, getContextForNonEmptyExpression(context), false);
        } else {
          addNonEmptyStatement(first.getNext(), Context.OTHER, false);
        }

        if (ambiguousElseClause) {
          cc.endBlock();
        }
        break;

      case NULL:
        Preconditions.checkState(childCount == 0, n);
        cc.addConstant("null");
        break;

      case THIS:
        Preconditions.checkState(childCount == 0, n);
        add("this");
        break;

      case SUPER:
        Preconditions.checkState(childCount == 0, n);
        add("super");
        break;

      case NEW_TARGET:
        Preconditions.checkState(childCount == 0, n);
        add("new.target");
        break;

      case YIELD:
        add("yield");
        if (n.isYieldAll()) {
          checkNotNull(first);
          add("*");
        }
        if (first != null) {
          cc.maybeInsertSpace();
          addExpr(first, NodeUtil.precedence(type), Context.OTHER);
        }
        break;

      case AWAIT:
        add("await ");
        addExpr(first, NodeUtil.precedence(type), Context.OTHER);
        break;

      case FALSE:
        Preconditions.checkState(childCount == 0, n);
        cc.addConstant("false");
        break;

      case TRUE:
        Preconditions.checkState(childCount == 0, n);
        cc.addConstant("true");
        break;

      case CONTINUE:
        Preconditions.checkState(childCount <= 1, n);
        add("continue");
        if (childCount == 1) {
          if (!first.isLabelName()) {
            throw new Error("Unexpected token type. Should be LABEL_NAME.");
          }
          add(" ");
          add(first);
        }
        cc.endStatement();
        break;

      case DEBUGGER:
        Preconditions.checkState(childCount == 0, n);
        add("debugger");
        cc.endStatement();
        break;

      case BREAK:
        Preconditions.checkState(childCount <= 1, n);
        add("break");
        if (childCount == 1) {
          if (!first.isLabelName()) {
            throw new Error("Unexpected token type. Should be LABEL_NAME.");
          }
          add(" ");
          add(first);
        }
        cc.endStatement();
        break;

      case EXPR_RESULT:
        Preconditions.checkState(childCount == 1, n);
        add(first, Context.START_OF_EXPR);
        cc.endStatement();
        break;

      case NEW:
        add("new ");
        int precedence = NodeUtil.precedence(type);

        // `new void 0` is a syntax error add parenthese in this case.  This is only particularly
        // interesting for code in dead branches.
        int precedenceOfFirst = NodeUtil.precedence(first.getToken());
        if (precedenceOfFirst == precedence) {
          precedence = precedence + 1;
        }

        // If the first child contains a CALL, then claim higher precedence
        // to force parentheses. Otherwise, when parsed, NEW will bind to the
        // first viable parentheses (don't traverse into functions).
        if (NodeUtil.containsType(first, Token.CALL, NodeUtil.MATCH_NOT_FUNCTION)) {
          precedence = NodeUtil.precedence(first.getToken()) + 1;
        }
        addExpr(first, precedence, Context.OTHER);

        // '()' is optional when no arguments are present
        Node next = first.getNext();
        if (next != null) {
          add("(");
          addList(next);
          add(")");
        }
        break;

      case STRING_KEY:
        addStringKey(n);
        break;

      case STRING:
        Preconditions.checkState(childCount == 0, "String node %s may not have children", n);
        addJsString(n);
        break;

      case DELPROP:
        Preconditions.checkState(childCount == 1, n);
        add("delete ");
        add(first);
        break;

      case OBJECTLIT:
        {
          boolean needsParens = (context == Context.START_OF_EXPR);
          if (needsParens) {
            add("(");
          }
          add("{");
          for (Node c = first; c != null; c = c.getNext()) {
            if (c != first) {
              cc.listSeparator();
            }

            checkState(NodeUtil.isObjLitProperty(c) || c.isSpread(), c);
            add(c);
          }
          add("}");
          if (needsParens) {
            add(")");
          }
          break;
        }

      case COMPUTED_PROP:
        maybeAddAccessibilityModifier(n);
        if (n.getBooleanProp(Node.STATIC_MEMBER)) {
          add("static ");
        }

        if (n.getBooleanProp(Node.COMPUTED_PROP_GETTER)) {
          add("get ");
        } else if (n.getBooleanProp(Node.COMPUTED_PROP_SETTER)) {
          add("set ");
        } else if (last.getBooleanProp(Node.GENERATOR_FN)) {
          add("*");
        } else if (last.isAsyncFunction()) {
          add("async");
        }
        add("[");
        add(first);
        add("]");
        // TODO(martinprobst): There's currently no syntax for properties in object literals that
        // have type declarations on them (a la `{foo: number: 12}`). This comes up for, e.g.,
        // function parameters with default values. Support when figured out.
        maybeAddTypeDecl(n);
        if (n.getBooleanProp(Node.COMPUTED_PROP_METHOD)
            || n.getBooleanProp(Node.COMPUTED_PROP_GETTER)
            || n.getBooleanProp(Node.COMPUTED_PROP_SETTER)) {
          Node function = first.getNext();
          Node params = function.getSecondChild();
          Node body = function.getLastChild();

          add(params);
          add(body);
        } else {
          // This is a field or object literal property.
          boolean isInClass = n.getParent().isClassMembers();
          Node initializer = first.getNext();
          if (initializer != null) {
            // Object literal value.
            checkState(
                !isInClass, "initializers should only exist in object literals, not classes");
            cc.addOp(":", false);
            add(initializer);
          } else {
            // Computed properties must either have an initializer or be computed member-variable
            // properties that exist for their type declaration.
            checkState(n.getBooleanProp(Node.COMPUTED_PROP_VARIABLE), n);
          }
        }
        break;

      case OBJECT_PATTERN:
        addObjectPattern(n);
        maybeAddTypeDecl(n);
        break;

      case SWITCH:
        add("switch(");
        add(first);
        add(")");
        cc.beginBlock();
        addAllSiblings(first.getNext());
        cc.endBlock(context == Context.STATEMENT);
        break;

      case CASE:
        Preconditions.checkState(childCount == 2, n);
        add("case ");
        add(first);
        addCaseBody(last);
        break;

      case DEFAULT_CASE:
        Preconditions.checkState(childCount == 1, n);
        add("default");
        addCaseBody(first);
        break;

      case LABEL:
        Preconditions.checkState(childCount == 2, n);
        if (!first.isLabelName()) {
          throw new Error("Unexpected token type. Should be LABEL_NAME.");
        }
        add(first);
        add(":");
        if (!last.isNormalBlock()) {
          cc.maybeInsertSpace();
        }
        addNonEmptyStatement(last, getContextForNonEmptyExpression(context), true);
        break;

      case CAST:
        if (preserveTypeAnnotations) {
          add("(");
        }
        add(first);
        if (preserveTypeAnnotations) {
          add(")");
        }
        break;

      case TAGGED_TEMPLATELIT:
        add(first, Context.START_OF_EXPR);
        add(first.getNext());
        break;

      case TEMPLATELIT:
        add("`");
        for (Node c = first; c != null; c = c.getNext()) {
          if (c.isString()) {
            add(strEscape(c.getString(), "\"", "'", "\\`", "\\\\", false, false));
          } else {
            // Can't use add() since isWordChar('$') == true and cc would add
            // an extra space.
            cc.append("${");
            add(c.getFirstChild(), Context.START_OF_EXPR);
            add("}");
          }
        }
        add("`");
        break;

        // Type Declaration ASTs.
      case STRING_TYPE:
        add("string");
        break;
      case BOOLEAN_TYPE:
        add("boolean");
        break;
      case NUMBER_TYPE:
        add("number");
        break;
      case ANY_TYPE:
        add("any");
        break;
      case VOID_TYPE:
        add("void");
        break;
      case NAMED_TYPE:
        // Children are a chain of getprop nodes.
        add(first);
        break;
      case ARRAY_TYPE:
        addExpr(first, NodeUtil.precedence(Token.ARRAY_TYPE), context);
        add("[]");
        break;
      case FUNCTION_TYPE:
        Node returnType = first;
        add("(");
        addList(first.getNext());
        add(")");
        cc.addOp("=>", true);
        add(returnType);
        break;
      case UNION_TYPE:
        addList(first, "|");
        break;
      case RECORD_TYPE:
        add("{");
        addList(first, false, Context.OTHER, ",");
        add("}");
        break;
      case PARAMETERIZED_TYPE:
        // First child is the type that's parameterized, later children are the arguments.
        add(first);
        add("<");
        addList(first.getNext());
        add(">");
        break;
        // CLASS -> NAME,EXPR|EMPTY,BLOCK
      case GENERIC_TYPE_LIST:
        add("<");
        addList(first, false, Context.STATEMENT, ",");
        add(">");
        break;
      case GENERIC_TYPE:
        addIdentifier(n.getString());
        if (n.hasChildren()) {
          add("extends");
          cc.maybeInsertSpace();
          add(n.getFirstChild());
        }
        break;
      case INTERFACE:
        {
          checkState(childCount == 3, n);
          Node name = first;
          Node superTypes = first.getNext();
          Node members = last;

          add("interface");
          add(name);
          maybeAddGenericTypes(name);
          if (!superTypes.isEmpty()) {
            add("extends");
            Node superType = superTypes.getFirstChild();
            add(superType);
            while ((superType = superType.getNext()) != null) {
              add(",");
              cc.maybeInsertSpace();
              add(superType);
            }
          }
          add(members);
        }
        break;
      case ENUM:
        {
          checkState(childCount == 2, n);
          Node name = first;
          Node members = last;
          add("enum");
          add(name);
          add(members);
          break;
        }
      case NAMESPACE:
        {
          checkState(childCount == 2, n);
          Node name = first;
          Node elements = last;
          add("namespace");
          add(name);
          add(elements);
          break;
        }
      case TYPE_ALIAS:
        add("type");
        add(n.getString());
        cc.addOp("=", true);
        add(last);
        cc.endStatement(true);
        break;
      case DECLARE:
        add("declare");
        add(first);
        processEnd(n, context);
        break;
      case INDEX_SIGNATURE:
        add("[");
        add(first);
        add("]");
        maybeAddTypeDecl(n);
        cc.endStatement(true);
        break;
      case CALL_SIGNATURE:
        if (n.getBooleanProp(Node.CONSTRUCT_SIGNATURE)) {
          add("new ");
        }
        maybeAddGenericTypes(n);
        add(first);
        maybeAddTypeDecl(n);
        cc.endStatement(true);
        break;
      default:
        throw new RuntimeException("Unknown token " + type + "\n" + n.toStringTree());
    }

    cc.endSourceMapping(n);
  }

  private void addIdentifier(String identifier) {
    cc.addIdentifier(identifierEscape(identifier));
  }

  private int precedence(Node n) {
    if (n.isCast()) {
      return precedence(n.getFirstChild());
    }
    return NodeUtil.precedence(n.getToken());
  }

  private static boolean arrowFunctionNeedsParens(Node n) {
    Node parent = n.getParent();

    // Once you cut through the layers of non-terminals used to define operator precedence,
    // you can see the following are true.
    // (Read => as "may expand to" and "!=>" as "may not expand to")
    //
    // 1. You can substitute an ArrowFunction into rules where an Expression or
    //    AssignmentExpression is required, because
    //      Expression => AssignmentExpression => ArrowFunction
    //
    // 2. However, most operators act on LeftHandSideExpression, CallExpression, or
    //    MemberExpression. None of these expand to ArrowFunction.
    //
    // 3. CallExpression cannot expand to an ArrowFunction at all, because all of its expansions
    //    produce multiple symbols and none can be logically equivalent to ArrowFunction.
    //
    // 4. LeftHandSideExpression and MemberExpression may be replaced with an ArrowFunction in
    //    parentheses, because:
    //      LeftHandSideExpression => MemberExpression => PrimaryExpression
    //      PrimaryExpression => '(' Expression ')' => '(' ArrowFunction ')'
    if (parent == null) {
      return false;
    } else if (NodeUtil.isBinaryOperator(parent)
        || NodeUtil.isUnaryOperator(parent)
        || NodeUtil.isUpdateOperator(parent)
        || parent.isTaggedTemplateLit()
        || parent.isGetProp()) {
      // LeftHandSideExpression OP LeftHandSideExpression
      // OP LeftHandSideExpression | LeftHandSideExpression OP
      // MemberExpression TemplateLiteral
      // MemberExpression '.' IdentifierName
      return true;
    } else if (parent.isGetElem() || parent.isCall() || parent.isHook()) {
      // MemberExpression '[' Expression ']'
      // MemberFunction '(' AssignmentExpressionList ')'
      // LeftHandSideExpression ? AssignmentExpression : AssignmentExpression
      return isFirstChild(n);
    } else {
      // All other cases are either illegal (e.g. because you cannot assign a value to an
      // ArrowFunction) or do not require parens.
      return false;
    }
  }

  private static boolean isFirstChild(Node n) {
    Node parent = n.getParent();
    return parent != null && n == parent.getFirstChild();
  }

  private void addArrowFunction(Node n, Node first, Node last, Context context) {
    checkState(first.getString().isEmpty(), first);
    boolean funcNeedsParens = arrowFunctionNeedsParens(n);
    if (funcNeedsParens) {
      add("(");
    }

    maybeAddGenericTypes(first);

    if (n.isAsyncFunction()) {
      add("async");
    }
    add(first.getNext()); // param list
    maybeAddTypeDecl(n);

    cc.addOp("=>", true);

    if (last.isNormalBlock()) {
      add(last);
    } else {
      // This is a hack. Arrow functions have no token type, but
      // blockless arrow function bodies have lower precedence than anything other than commas.
      addExpr(last, NodeUtil.precedence(Token.COMMA) + 1, context);
    }
    cc.endFunction(context == Context.STATEMENT);

    if (funcNeedsParens) {
      add(")");
    }
  }

  private void addFunction(Node n, Node first, Node last, Context context) {
    boolean funcNeedsParens = (context == Context.START_OF_EXPR);
    if (funcNeedsParens) {
      add("(");
    }

    add(n.isAsyncFunction() ? "async function" : "function");
    if (n.isGeneratorFunction()) {
      add("*");
      if (!first.getString().isEmpty()) {
        cc.maybeInsertSpace();
      }
    }

    add(first);
    maybeAddGenericTypes(first);

    add(first.getNext()); // param list
    maybeAddTypeDecl(n);

    add(last);
    cc.endFunction(context == Context.STATEMENT);

    if (funcNeedsParens) {
      add(")");
    }
  }

  private void maybeAddAccessibilityModifier(Node n) {
    Visibility access = (Visibility) n.getProp(Node.ACCESS_MODIFIER);
    if (access != null) {
      add(access.toString().toLowerCase() + " ");
    }
  }

  private void maybeAddTypeDecl(Node n) {
    if (n.getDeclaredTypeExpression() != null) {
      add(":");
      cc.maybeInsertSpace();
      add(n.getDeclaredTypeExpression());
    }
  }

  private void maybeAddGenericTypes(Node n) {
    Node generics = (Node) n.getProp(Node.GENERIC_TYPE_LIST);
    if (generics != null) {
      add(generics);
    }
  }

  private void maybeAddOptional(Node n) {
    if (n.getBooleanProp(Node.OPT_ES6_TYPED)) {
      add("?");
    }
  }

  /**
   * We could use addList recursively here, but sometimes we produce
   * very deeply nested operators and run out of stack space, so we
   * just unroll the recursion when possible.
   *
   * We assume nodes are left-recursive.
   */
  private void unrollBinaryOperator(
      Node n, Token op, String opStr, Context context,
      Context rhsContext, int leftPrecedence, int rightPrecedence) {
    Node firstNonOperator = n.getFirstChild();
    while (firstNonOperator.getToken() == op) {
      firstNonOperator = firstNonOperator.getFirstChild();
    }

    addExpr(firstNonOperator, leftPrecedence, context);

    Node current = firstNonOperator;
    do {
      current = current.getParent();
      cc.addOp(opStr, true);
      addExpr(current.getSecondChild(), rightPrecedence, rhsContext);
    } while (current != n);
  }

  static boolean isSimpleNumber(String s) {
    int len = s.length();
    if (len == 0) {
      return false;
    }
    for (int index = 0; index < len; index++) {
      char c = s.charAt(index);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return len == 1 || s.charAt(0) != '0';
  }

  static double getSimpleNumber(String s) {
    if (isSimpleNumber(s)) {
      try {
        long l = Long.parseLong(s);
        if (l < NodeUtil.MAX_POSITIVE_INTEGER_NUMBER) {
          return l;
        }
      } catch (NumberFormatException e) {
        // The number was too long to parse. Fall through to NaN.
      }
    }
    return Double.NaN;
  }

  /**
   * @return Whether the name is an indirect eval.
   */
  private static boolean isIndirectEval(Node n) {
    return n.isName() && "eval".equals(n.getString()) && !n.getBooleanProp(Node.DIRECT_EVAL);
  }

  /**
   * Adds a block or expression, substituting a VOID with an empty statement.
   * This is used for "for (...);" and "if (...);" type statements.
   *
   * @param n The node to print.
   * @param context The context to determine how the node should be printed.
   */
  private void addNonEmptyStatement(
      Node n, Context context, boolean allowNonBlockChild) {
    Node nodeToProcess = n;

    if (!allowNonBlockChild && !n.isNormalBlock()) {
      throw new Error("Missing BLOCK child.");
    }

    // Strip unneeded blocks, that is blocks with <2 children unless
    // the CodePrinter specifically wants to keep them.
    if (n.isNormalBlock()) {
      int count = getNonEmptyChildCount(n, 2);
      if (count == 0) {
        if (cc.shouldPreserveExtraBlocks()) {
          cc.beginBlock();
          cc.endBlock(cc.breakAfterBlockFor(n, context == Context.STATEMENT));
        } else {
          cc.endStatement(true);
        }
        return;
      }

      if (count == 1) {
        // Preserve the block only if needed or requested.
        //'let', 'const', etc are not allowed by themselves in "if" and other
        // structures. Also, hack around a IE6/7 browser bug that needs a block around DOs.
        Node firstAndOnlyChild = getFirstNonEmptyChild(n);
        boolean alwaysWrapInBlock = cc.shouldPreserveExtraBlocks();
        if (alwaysWrapInBlock || isBlockDeclOrDo(firstAndOnlyChild)) {
          cc.beginBlock();
          add(firstAndOnlyChild, Context.STATEMENT);
          cc.maybeLineBreak();
          cc.endBlock(cc.breakAfterBlockFor(n, context == Context.STATEMENT));
          return;
        } else {
          // Continue with the only child.
          nodeToProcess = firstAndOnlyChild;
        }
      }
    }

    if (nodeToProcess.isEmpty()) {
      cc.endStatement(true);
    } else {
      add(nodeToProcess, context);
    }
  }

  /**
   * @return Whether the Node is a DO or a declaration that is only allowed
   * in restricted contexts.
   */
  private static boolean isBlockDeclOrDo(Node n) {
    if (n.isLabel()) {
      Node labeledStatement = n.getLastChild();
      if (!labeledStatement.isNormalBlock()) {
        return isBlockDeclOrDo(labeledStatement);
      } else {
        // For labels with block children, we need to ensure that a
        // labeled FUNCTION or DO isn't generated when extraneous BLOCKs
        // are skipped.
        if (getNonEmptyChildCount(n, 2) == 1) {
          return isBlockDeclOrDo(getFirstNonEmptyChild(n));
        } else {
          // Either a empty statement or an block with more than one child,
          // way it isn't a FUNCTION or DO.
          return false;
        }
      }
    } else {
      switch (n.getToken()) {
        case LET:
        case CONST:
        case FUNCTION:
        case CLASS:
        case DO:
          return true;
        default:
          return false;
      }
    }
  }

  private void addExpr(Node n, int minPrecedence, Context context) {
    if (opRequiresParentheses(n, minPrecedence, context)) {
      add("(");
      add(n, Context.OTHER);
      add(")");
    } else {
      add(n, context);
    }
  }

  private boolean opRequiresParentheses(Node n, int minPrecedence, Context context) {
    if (context == Context.IN_FOR_INIT_CLAUSE && n.isIn()) {
      // make sure this operator 'in' isn't confused with the for-loop 'in'
      return true;
    } else if (NodeUtil.isUnaryOperator(n) && isFirstOperandOfExponentiationExpression(n)) {
      // Unary operators are higher precedence than '**', but
      // ExponentiationExpression cannot expand to
      //     UnaryExpression ** ExponentiationExpression
      return true;
    } else if (isObjectLitOrCastOfObjectLit(n) && n.getParent().isArrowFunction()) {
      // If the body of an arrow function is an object literal, the braces are treated as a
      // statement block with higher precedence, which we avoid with parentheses.
      return true;
    } else {
      return precedence(n) < minPrecedence;
    }
  }

  private boolean isObjectLitOrCastOfObjectLit(Node n) {
    return n.isObjectLit() || (n.isCast() && n.getFirstChild().isObjectLit());
  }

  private boolean isFirstOperandOfExponentiationExpression(Node n) {
    Node parent = n.getParent();
    return parent != null && parent.getToken() == Token.EXPONENT && parent.getFirstChild() == n;
  }

  void addList(Node firstInList) {
    addList(firstInList, true, Context.OTHER, ",");
  }

  void addList(Node firstInList, String separator) {
    addList(firstInList, true, Context.OTHER, separator);
  }

  void addList(Node firstInList, boolean isArrayOrFunctionArgument,
      Context lhsContext, String separator) {
    for (Node n = firstInList; n != null; n = n.getNext()) {
      boolean isFirst = n == firstInList;
      if (isFirst) {
        addExpr(n, isArrayOrFunctionArgument ? 1 : 0, lhsContext);
      } else {
        cc.addOp(separator, true);
        addExpr(n, isArrayOrFunctionArgument ? 1 : 0,
            getContextForNoInOperator(lhsContext));
      }
    }
  }

  void addStringKey(Node n) {
    String key = n.getString();
    // Object literal property names don't have to be quoted if they are not JavaScript keywords.
    boolean mustBeQuoted =
        n.isQuotedString()
        || (quoteKeywordProperties && TokenStream.isKeyword(key))
        || !TokenStream.isJSIdentifier(key)
        // do not encode literally any non-literal characters that were Unicode escaped.
        || !NodeUtil.isLatin(key);
    if (!mustBeQuoted) {
      // Check if the property is eligible to be printed as shorthand.
      if (n.isShorthandProperty()) {
        Node child = n.getFirstChild();
        if (child.matchesQualifiedName(key)
            || (child.isDefaultValue() && child.getFirstChild().matchesQualifiedName(key))) {
          add(child);
          return;
        }
      }
      add(key);
    } else {
      // Determine if the string is a simple number.
      double d = getSimpleNumber(key);
      if (!Double.isNaN(d)) {
        cc.addNumber(d, n);
      } else {
        addJsString(n);
      }
    }
    if (n.hasChildren()) {
      // NOTE: the only time a STRING_KEY node does *not* have children is when it's
      // inside a TypeScript enum.  We should change these to their own ENUM_KEY token
      // so that the bifurcating logic can be removed from STRING_KEY.
      add(":");
      addExpr(n.getFirstChild(), 1, Context.OTHER);
    }
  }

  void addObjectPattern(Node n) {
    add("{");
    for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
      if (child != n.getFirstChild()) {
        cc.listSeparator();
      }

      add(child);
    }
    add("}");
  }

  /**
   * This function adds a comma-separated list as is specified by an ARRAYLIT
   * node with the associated skipIndexes array.  This is a space optimization
   * since we avoid creating a whole Node object for each empty array literal
   * slot.
   * @param firstInList The first in the node list (chained through the next
   * property).
   */
  void addArrayList(Node firstInList) {
    boolean lastWasEmpty = false;
    for (Node n = firstInList; n != null; n = n.getNext()) {
      if (n != firstInList) {
        cc.listSeparator();
      }
      addExpr(n, 1, Context.OTHER);
      lastWasEmpty = n.isEmpty();
    }

    if (lastWasEmpty) {
      cc.listSeparator();
    }
  }

  void addCaseBody(Node caseBody) {
    checkState(caseBody.isNormalBlock(), caseBody);
    cc.beginCaseBody();
    addAllSiblings(caseBody.getFirstChild());
    cc.endCaseBody();
  }

  void addAllSiblings(Node n) {
    for (Node c = n; c != null; c = c.getNext()) {
      add(c);
    }
  }

  /** Outputs a JS string, using the optimal (single/double) quote character */
  private void addJsString(Node n) {
    String s = n.getString();
    boolean useSlashV = n.getBooleanProp(Node.SLASH_V);
    if (useSlashV) {
      add(jsString(n.getString(), useSlashV));
    } else {
      String cached = escapedJsStrings.get(s);
      if (cached == null) {
        cached = jsString(n.getString(), useSlashV);
        escapedJsStrings.put(s, cached);
      }
      add(cached);
    }
  }

  private String jsString(String s, boolean useSlashV) {
    int singleq = 0;
    int doubleq = 0;

    // could count the quotes and pick the optimal quote character
    for (int i = 0; i < s.length(); i++) {
      switch (s.charAt(i)) {
        case '"': doubleq++; break;
        case '\'': singleq++; break;
        default: // skip non-quote characters
      }
    }

    String doublequote;
    String singlequote;
    char quote;
    if (preferSingleQuotes ? (singleq <= doubleq) : (singleq < doubleq)) {
      // more double quotes so enclose in single quotes.
      quote = '\'';
      doublequote = "\"";
      singlequote = "\\\'";
    } else {
      // more single quotes so escape the doubles
      quote = '\"';
      doublequote = "\\\"";
      singlequote = "\'";
    }

    return quote + strEscape(s, doublequote, singlequote, "`", "\\\\", useSlashV, false) + quote;
  }

  /** Escapes regular expression */
  String regexpEscape(String s) {
    return '/' + strEscape(s, "\"", "'", "`", "\\", false, true) + '/';
  }

  /** Helper to escape JavaScript string as well as regular expression */
  private String strEscape(
      String s,
      String doublequoteEscape,
      String singlequoteEscape,
      String backtickEscape,
      String backslashEscape,
      boolean useSlashV,
      boolean isRegexp) {
    StringBuilder sb = new StringBuilder(s.length() + 2);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '\0': sb.append("\\x00"); break;
        case '\u000B':
          if (useSlashV) {
            sb.append("\\v");
          } else {
            sb.append("\\x0B");
          }
          break;
        // From the SingleEscapeCharacter grammar production.
        case '\b': sb.append("\\b"); break;
        case '\f': sb.append("\\f"); break;
        case '\n': sb.append("\\n"); break;
        case '\r': sb.append("\\r"); break;
        case '\t': sb.append("\\t"); break;
        case '\\': sb.append(backslashEscape); break;
        case '\"': sb.append(doublequoteEscape); break;
        case '\'': sb.append(singlequoteEscape); break;
        case '`': sb.append(backtickEscape); break;

        // From LineTerminators (ES5 Section 7.3, Table 3)
        case '\u2028': sb.append("\\u2028"); break;
        case '\u2029': sb.append("\\u2029"); break;

        case '=':
          // '=' is a syntactically significant regexp character.
          if (trustedStrings || isRegexp) {
            sb.append(c);
          } else {
            sb.append("\\x3d");
          }
          break;

        case '&':
          if (trustedStrings || isRegexp) {
            sb.append(c);
          } else {
            sb.append("\\x26");
          }
          break;

        case '>':
          if (!trustedStrings && !isRegexp) {
            sb.append(GT_ESCAPED);
            break;
          }

          // Break --> into --\> or ]]> into ]]\>
          //
          // This is just to prevent developers from shooting themselves in the
          // foot, and does not provide the level of security that you get
          // with trustedString == false.
          if (i >= 2
              && ((s.charAt(i - 1) == '-' && s.charAt(i - 2) == '-')
                  || (s.charAt(i - 1) == ']' && s.charAt(i - 2) == ']'))) {
            sb.append(GT_ESCAPED);
          } else {
            sb.append(c);
          }
          break;
        case '<':
          if (!trustedStrings && !isRegexp) {
            sb.append(LT_ESCAPED);
            break;
          }

          // Break </script into <\/script
          // As above, this is just to prevent developers from doing this
          // accidentally.
          final String endScript = "/script";

          // Break <!-- into <\!--
          final String startComment = "!--";

          if (s.regionMatches(true, i + 1, endScript, 0, endScript.length())) {
            sb.append(LT_ESCAPED);
          } else if (s.regionMatches(false, i + 1, startComment, 0, startComment.length())) {
            sb.append(LT_ESCAPED);
          } else {
            sb.append(c);
          }
          break;
        default:
          if ((outputCharsetEncoder != null && outputCharsetEncoder.canEncode(c))
              || (c > 0x1f && c < 0x7f)) {
            // If we're given an outputCharsetEncoder, then check if the character can be
            // represented in this character set. If no charsetEncoder provided - pass straight
            // Latin characters through, and escape the rest. Doing the explicit character check is
            // measurably faster than using the CharsetEncoder.
            sb.append(c);
          } else {
            // Other characters can be misinterpreted by some JS parsers,
            // or perhaps mangled by proxies along the way,
            // so we play it safe and Unicode escape them.
            Util.appendHexJavaScriptRepresentation(sb, c);
          }
      }
    }
    return sb.toString();
  }

  static String identifierEscape(String s) {
    // First check if escaping is needed at all -- in most cases it isn't.
    if (NodeUtil.isLatin(s)) {
      return s;
    }

    // Now going through the string to escape non-Latin characters if needed.
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      // Identifiers should always go to Latin1/ ASCII characters because
      // different browser's rules for valid identifier characters are
      // crazy.
      if (c > 0x1F && c < 0x7F) {
        sb.append(c);
      } else {
        Util.appendHexJavaScriptRepresentation(sb, c);
      }
    }
    return sb.toString();
  }
  /**
   * @param maxCount The maximum number of children to look for.
   * @return The number of children of this node that are non empty up to
   * maxCount.
   */
  private static int getNonEmptyChildCount(Node n, int maxCount) {
    int i = 0;
    Node c = n.getFirstChild();
    for (; c != null && i < maxCount; c = c.getNext()) {
      if (c.isNormalBlock()) {
        i += getNonEmptyChildCount(c, maxCount - i);
      } else if (!c.isEmpty()) {
        i++;
      }
    }
    return i;
  }

  /** Gets the first non-empty child of the given node. */
  private static Node getFirstNonEmptyChild(Node n) {
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if (c.isNormalBlock()) {
        Node result = getFirstNonEmptyChild(c);
        if (result != null) {
          return result;
        }
      } else if (!c.isEmpty()) {
        return c;
      }
    }
    return null;
  }

  /**
   * Information on the current context. Used for disambiguating special cases.
   * For example, a "{" could indicate the start of an object literal or a
   * block, depending on the current context.
   */
  public enum Context {
    STATEMENT,
    BEFORE_DANGLING_ELSE, // a hack to resolve the else-clause ambiguity
    START_OF_EXPR,
    // Are we inside the init clause of a for loop?  If so, the containing
    // expression can't contain an in operator.  Pass this context flag down
    // until we reach expressions which no longer have the limitation.
    IN_FOR_INIT_CLAUSE,
    OTHER
  }

  private static Context getContextForNonEmptyExpression(Context currentContext) {
    return currentContext == Context.BEFORE_DANGLING_ELSE
        ? Context.BEFORE_DANGLING_ELSE
        : Context.OTHER;
  }

  /**
   * If we're in a IN_FOR_INIT_CLAUSE, we can't permit in operators in the
   * expression.  Pass on the IN_FOR_INIT_CLAUSE flag through subexpressions.
   */
  private static Context getContextForNoInOperator(Context context) {
    return (context == Context.IN_FOR_INIT_CLAUSE
        ? Context.IN_FOR_INIT_CLAUSE : Context.OTHER);
  }

  private void processEnd(Node n, Context context) {
    switch (n.getToken()) {
      case CLASS:
      case INTERFACE:
      case ENUM:
      case NAMESPACE:
        cc.endClass(context == Context.STATEMENT);
        break;
      case FUNCTION:
        if (n.getLastChild().isEmpty()) {
          cc.endStatement(true);
        } else {
          cc.endFunction(context == Context.STATEMENT);
        }
        break;
      case DECLARE:
        if (n.getParent().getToken() != Token.NAMESPACE_ELEMENTS) {
          processEnd(n.getFirstChild(), context);
        }
        break;
      case EXPORT:
        if (n.getParent().getToken() != Token.NAMESPACE_ELEMENTS
            && n.getFirstChild().getToken() != Token.DECLARE) {
          processEnd(n.getFirstChild(), context);
        }
        break;
      case COMPUTED_PROP:
        if (n.hasOneChild()) {
          cc.endStatement(true);
        }
        break;
      case MEMBER_FUNCTION_DEF:
      case GETTER_DEF:
      case SETTER_DEF:
        if (n.getFirstChild().getLastChild().isEmpty()) {
          cc.endStatement(true);
        }
        break;
      case MEMBER_VARIABLE_DEF:
        cc.endStatement(true);
        break;
      default:
        if (context == Context.STATEMENT) {
          cc.endStatement();
        }
    }
  }

  // Code added by James Hutchins to replace node "requires"
  // JAMES
  public Node replaceRequire(Node n){
    //ReqResLog("Replace require was called for: ");
    // Node Set up (NOT SURE IF I ACTUALLY NEED THESE)
    Node finalNode = null;
    Node currNode = n;
    // Gets file location and extension
    String modName = findModuleName(currNode);
    if(modName==null) return null;
    String path = getRequirePath(modName,currNode);
    if(path==null) return null;
    // See if file has a variable already
    String DFSVar = findVarName(path);
    if(!DFSVar.equals("")){ // If it does replace it with variable name... DONE
      ReqResLog("The variable was found, replacing require with variable\n");
      // TODO check if js, json, or other
      add(DFSVar+".exports"); // TODO preseve the stuff attached (so var x = require("mod").x1, .x1 would be "attached")
      currNode = currNode.getFirstChild().getNext(); // FIRST ATTEMPT TO GET TO ATTACHED CHILD
      return currNode;
    }
    DFSVar = assignVarName(path);
    ReqResLog("Replace require was called for: ");
    ReqResLog(path+"\n");
    String extension = path.substring(path.lastIndexOf("."),path.length());

    // Call wrappers based on type
    if(extension.equalsIgnoreCase(".js")){
      wrapJavaScript(path,DFSVar);
    }else if(extension.equalsIgnoreCase(".json")){
      wrapJSON(path,DFSVar);
    }else if (extension.equalsIgnoreCase(".node")){
      ReqResLog("[ Important ] .node is a valid file type, but I do not have a wrapper for it\n");
      currNode = null;
    }else if (extension.equalsIgnoreCase(".mjs")){
      ReqResLog("[ Important ] .mjs is used for experimental modules. I do not have a wrapper for it\n");
    }else{
      ReqResLog("This is an unsupported file type\n");
    }

    return currNode;
  }
  // These are the wrappers
  public void wrapJavaScript(String path,String DFSVar){
    // Wrapper Creation
    String startWrapper = "(function(){\n" +
            "\t\tvar module = { exports: {} };\n" +
            "\t\t"+DFSVar+" = module;\n" +
            "\t\t(function(exports, module, __filename, __dirname) {\n\n";
    String beforePaths = "\n\n\t\t})(module.exports,module,";
    String afterPaths = ";\n\t\treturn module.exports\n\t}())";

    // using path start a new closure compiler instance to get the code
    String pathString = "\""+path+"\"";
    String dirString = "\""+path.substring(0,path.lastIndexOf("/"))+"\"";
    String code = compileCode(path); // TODO see if we need a better way to do this

    // Actually adding the wrapper to the file
    add(startWrapper);
    add(code); // Code to be wrapped...
    add(beforePaths);
    add(pathString); // Full path + file name
    add(",");
    add(dirString); // Full path
    add(")");
    add(afterPaths);
  }
  public void wrapJSON(String path, String DFSVar){
    // This still seems wrong, so we will need to test it I think
    // IF we could use the below that would be great
    //Compiler.processJsonInputs(path);
    //TODO convert this to JavaScript
    String code = readFile(path);
    String aV = "function(){\n";
    String globalVar = "\t"+DFSVar+" = { exports: {} };\n";
    String JSON = "\t"+DFSVar+".exports = JSON.parse";
    String codeToWrite = code.replace("\r\n","").replace("\n","");//.replace('"','\\\"').replace("'","\\\'");
    codeToWrite = codeToWrite.replace("\\\\","/"); //TODO Test
    codeToWrite = codeToWrite.replace("\'","\\'"); //TODO test

    add(aV);
    add(globalVar);
    add(JSON);
    add("(");
    add("\n");
    add("'"+codeToWrite+"'"); // Code to be wrapped...
    add("\n");
    add("\t);");
    add("\n");
    add("\treturn "+DFSVar+".exports;");
    add("}()");
  }
  // These are for finding the file that needs to be added
  public String getRequirePath(String m, Node n){// throws java.io.IOException{
    String path = "";String out = "";
    // Create Command and input
    String req = "require.resolve(\""+m+"\");\n.exit\n";
    String[] command = {this.nodePref,"--interactive"};//,req};
    // Get Directory to run the command in
    String currentPath = n.getSourceFileName(); // Need to get current path
    int index = currentPath.lastIndexOf("/");
    currentPath = currentPath.substring(0,index+1);
    // Call Command
    out = callCommand(command,currentPath,req);
    // Process output into path
    path = out.substring(3,out.lastIndexOf('>')-2);
    path = builtInModCheck(m,path);
    if(path == null) return path;
    // Make path use foward slash
    path = path.replace("\\\\","\\").replace("\\","/");
    return path;
  }
  public String builtInModCheck(String m, String path){
    if(m.equalsIgnoreCase(path)){
      ReqResLog("This might be a source module: ");
      if(!this.resolveNJSSC){return null;}
      ReqResLog(m);ReqResLog("\n");
      path = this.sourceNodeCode+"\\lib"+"\\"+m+".js"; // pathToNative+file-seporator+moduleName+.js
      checkFileExistance(path,m); // TODO make it so it checks c coded modules. And change variable name?
    }
    String errorString = "rror: Cannot find module '";
    if(path.startsWith(errorString)) {
      ReqResLog("This might be a internal source module: ");
      if(!this.resolveNJSSC){return null;}
      String temp = path.split("'")[1];
      if (temp.startsWith("internal/")) {
        ReqResLog(temp);ReqResLog("\n");
        path = this.sourceNodeCode+"\\lib" + "\\" + temp + ".js";
      } else {
        // async_hooks is the reason for this. It gives the error message an internal/ does, but is not an internal/
        path = this.sourceNodeCode+"\\lib" + "\\" + temp + ".js";
        checkFileExistance(path,temp);
      }
    }
    return path;
  }
  public boolean checkFileExistance(String path,String temp){
    boolean exists = true;
    try {
      File f = new File(path);
      if (!f.exists()) {
        exists = false;
        ReqResLog(temp);
        ReqResLog(" DOES NOT appear to exist\n[ VERY IMPORTANT ] A path may need to be provided\n");
      }
      //f.close();
    } catch (Exception e) {
      exists = false;
      ReqResLog(temp);
      ReqResLog(" DOES NOT appear to exist\n");
    }
    return exists;
  }
  public String findModuleName(Node n){
    String modName = "";
    n = n.getFirstChild().getNext();
    if(n.getToken() != Token.STRING){
      ReqResLog("[ Very Important ]: REQUIRE There appears to have been a dynamic require of some sort.\n\t"+
              "Currently we do not handle these\n");
      return null; // For now ignore requires that do not use strings.
    }
    modName = n.getString().replace("\\\\","\\").replace("\\","/");
    return modName;
  }
  // This checks to see if it is require or module.require
  public Node forwardCheckNodeRequire(Node first){
    Node n = null;
    if(first != null){
      Node tempNode = first; Token tempToken = first.getToken();
      // Checks to see if GETPROP is module.
      if(tempToken == Token.GETPROP) {
        //while(tempToken == Token.GETPROP){tempNode = tempNode.getFirstChild();tempToken = tempNode.getToken();} // Inside child_process there is a double get_prop. I thought get prop is something.something, so not sure how that worked
        tempNode = tempNode.getFirstChild();tempToken = tempNode.getToken();
        if(tempToken == Token.NAME) {
          if (tempNode.getOriginalName() != null) {
            if (tempNode.getString().compareTo("module") == 0 || tempNode.getOriginalName().compareTo("module") == 0) {
              tempNode = tempNode.getNext();
              tempToken = tempNode.getToken();
            }
          } else if (tempNode.getString().compareTo("module") == 0) {
            tempNode = tempNode.getNext();
            tempToken = tempNode.getToken();
          }
        }
      }
      // Checks to see if NAME OR STRING is require
      if (tempToken == Token.NAME) {
        if(tempNode.getOriginalName() != null) {
          if (tempNode.getString().compareTo("require") == 0 || tempNode.getOriginalName().compareTo("require") == 0) {
            n = replaceRequire(tempNode.getParent());
          }
        }else if (tempNode.getString().compareTo("require") == 0){
          n = replaceRequire(tempNode.getParent());
        }
      }else if(tempToken == Token.STRING){ // Apparently require is a STRING when it is part of module.require
        if(tempNode.getOriginalName() != null) {
          if (tempNode.getString().compareTo("require") == 0 || tempNode.getOriginalName().compareTo("require") == 0) {
            n = replaceRequire(tempNode.getParent().getParent());
          }
        }else if (tempNode.getString().compareTo("require") == 0){
          n = replaceRequire(tempNode.getParent().getParent());
        }
      }
    }
    return n;
  }
  // This calls a command given the command in String[], a path for the directory, and inputs not able to be stored in the "command"
  public String callCommand(String[] cmd,String path,String input){
    String out = "";
    // Convert Commands to list for ProcessBuilder # If I uses Runtime.exec then it needs String[]
    List<String> cmdList = new ArrayList<String>();
    for(int i=0;i<cmd.length;i++){ cmdList.add(cmd[i]); }
    try{
      // THESE TWO LINES APPEAR TO BE EQUEVALENT
      //Process proc = Runtime.getRuntime().exec(cmd,null,new File(currentPath));
      ProcessBuilder p = new ProcessBuilder(cmdList);p.directory(new File(path));Process proc = p.start();

      // Writes commands that are not arguments to the process
      proc.getOutputStream().write(input.getBytes("UTF-8"));
      String temp = "";for(int i = 0;i<cmd.length;i++){ temp = temp+" "+cmd[i];}
      ReqResLog("Calling command: "+temp+"\n");
      proc.getOutputStream().close();

      // Read results
      out = readInput(proc.getInputStream());
      if(out == ""){
        ReqResLog(readInput(proc.getErrorStream()));
        ReqResLog("\n");
      }
    }catch (Exception e){
      out = "An error occurred inside callCommand (CodeGenerator.java)";
      ReqResLog(out+":\n");
      ReqResLog(e.toString());
      ReqResLog("\n");
    }
    return out;
  }
  public String readInput(InputStream in){
    String s = null; String out = "";
    try{
      BufferedReader stdInput = new BufferedReader(new InputStreamReader(in));
      while ((s = stdInput.readLine()) != null) {
        if(out.equals("")){
          out = s;
        }else {
          out = out + "\n" + s;
        }
      }
    }catch (Exception e){
      out = "An error occurred inside readInput (CodeGenerator.java)";
      ReqResLog(out+":\n");
      ReqResLog(e.toString());
      ReqResLog("\n");
    }
    return out;
  }
  // This reads raw file into the script
  public String readFile(String path){
    String code = "";
    String sc = null;
    try{
      BufferedReader br = new BufferedReader(new FileReader(path));
      while (( sc = br.readLine()) != null) {
        if(code.equals("")){
          code = sc;
        }else{
          code = code+"\n"+sc;
        }
      }
    }catch (Exception e){
      String out = "An error occurred inside makeSourceFile (CodeGenerator.java)";
      ReqResLog(out+":\n");
      ReqResLog(e.toString());
      ReqResLog("\n");
    }
    return code;
  }
  // This creates a new instance of closure compiler and runs the new JS threw it. (It also calls get command)
  public String compileCode(String path){
    String code = "//code";
    // TODO replace "java" with path to java var... in fact change the third one to var also
    // TODO replace hardcoded path with Variable path
    // TODO see if I (need to) can I call it without console commands
    storeCurrentDFSRequireResults();
    String[] command = getCommand(path);
    String out = callCommand(command,"D:\\Sefcom\\closure\\closure-compiler-myAttempt","\n");
    code = out;
    getCurrentDFSRequireResults();
    return code;
  }
  public String[] getCommand(String path){
    // TODO Change "java" and <location of jar> to varriables.
    // TODO add new options
    if(this.nodePref.equalsIgnoreCase("node")) {
      String[] command = {
              "java", "-jar",
              "D:\\Sefcom\\closure\\closure-compiler-myAttempt\\target\\closure-compiler-1.0-SNAPSHOT.jar",
              "--module_resolution", "NODE", "--js", path,"--compilation_level",this.compLevel, "--formatting",
              "PRETTY_PRINT","--language_out",this.langOut,"--require_resolve_log_location", this.reqreslogloc,
              "--nodejs_source", this.sourceNodeCode, "--DFS_tracking_log_location", this.dfsResultFilename,
              "--resolve_NSC",String.valueOf(this.resolveNJSSC)
      };
      return command;
    }else {
      String[] command = {
              "java", "-jar",
              "D:\\Sefcom\\closure\\closure-compiler-myAttempt\\target\\closure-compiler-1.0-SNAPSHOT.jar",
              "--module_resolution", "NODE", "--js", path,"--compilation_level",this.compLevel, "--formatting",
              "PRETTY_PRINT","--language_out",this.langOut,"--require_resolve_log_location", this.reqreslogloc,
              "--nodejs_source", this.sourceNodeCode, "--DFS_tracking_log_location", this.dfsResultFilename,
              "--node_exe_path", this.nodePref,"--resolve_NSC",String.valueOf(this.resolveNJSSC)
      };
      return command;
    }
  }
  // These are the logging varriables and functions
  private PrintWriter rrl = null;
  private String reqreslogloc = null;
  public void initReqResLog(boolean logReset){
    // Assign log name if one does not exist
    if(this.reqreslogloc == null){ this.reqreslogloc = ("../resreqlog.txt"); }
    // If we need to reset the log, do that
    if(logReset){
      try{
        this.rrl = new PrintWriter(new BufferedWriter(new FileWriter(this.reqreslogloc,false)));
        String msg = "Log file \""+this.reqreslogloc+"\" has been reset.";
        this.rrl.println(msg);
        this.rrl.close();
      }catch (Exception e){
        String out = "An error occurred inside initReqResLog > file (CodeGenerator.java)";
        System.out.println(out);
        System.out.println(e);
      }
    }
  }
  public void reInLog(){
    try {
      this.rrl = new PrintWriter(new BufferedWriter(new FileWriter(this.reqreslogloc, true)));
    } catch (Exception e) {
      String out = "An error occurred inside reInLog (CodeGenerator.java)";
      System.out.println(out);
      System.out.println(e);
    }
  }
  public void ReqResLog(String s){
    if(this.reqreslogloc == null){
      System.out.print(s);
    }else{
      try {
        reInLog();
        this.rrl.print(s);
        this.rrl.close();
      } catch (Exception e) {
        String out = "An error occurred inside ReqResLog (CodeGenerator.java)";
        System.out.println(out);
        System.out.println(e);
      }
    }
  }
  // These are the loop resolving specifics
  private int currentVar = 0;
  private String baseVarName = "globalVariable_SHYDNUTN_";// Stands for Global Variable: Sure Hope You Did Not Use This Name: ID Number
  private List<String> filesToVar = new ArrayList<String>();
  private String dfsResultFilename = null;
  public String varNameCreation(int x){
    String name = null;
    if(x >= 0 && x < 10){
      name = baseVarName+"00"+String.valueOf(x);
    }else if(x >= 10 && x < 100){
      name = baseVarName+"0"+String.valueOf(x);
    }else if(x >= 100){
      name = baseVarName+String.valueOf(x);
    }else{
      ReqResLog("An invalid number was used to try and create a global variable. ");
      ReqResLog("The number was: "+String.valueOf(x));
    }
    //ReqResLog("INFO: a variable "+name+" was created to handle ");
    return name;
  }
  public String assignVarName(String fn){
    this.filesToVar.add(fn);
    currentVar++;
    return varNameCreation(currentVar-1);
  }
  public String findVarName(String absoluteFileName){
    absoluteFileName = absoluteFileName.replace("\\\\","\\").replace("\\","/");
    if(this.filesToVar.size() > currentVar) {
      ReqResLog("filesToVar.length: " + String.valueOf(this.filesToVar.size()) + " vs currentVar: " + String.valueOf(currentVar)+"\n");
    }
    for(int i=0;i<this.filesToVar.size();i++) {
      if (absoluteFileName.equalsIgnoreCase(this.filesToVar.get(i))) {
        ReqResLog(absoluteFileName + "==" + this.filesToVar.get(i)+"\n");
        return varNameCreation(i);
      }
    }
    ReqResLog(absoluteFileName+" did not have a variable already.\n");
    return "";
  }
  public void initDFSRR(boolean logReset){
    if(dfsResultFilename == null) { this.dfsResultFilename = "../DFSMapping.txt"; }
    if(logReset){
      storeCurrentDFSRequireResults();
    }else{
      getCurrentDFSRequireResults();
    }
  }
  public void getCurrentDFSRequireResults(){
    BufferedReader dfsResults = (BufferedReader) openFile("open",this.dfsResultFilename,false);
    if(dfsResults == null){
      ReqResLog("Could not retrieve DFS results\n");
      return;
    }
    try{
      // Read line
      String line = dfsResults.readLine();
      // Store first line (number of variables so far in a temp variable as to not mess with quality checks
      int totalTemp = 0;
      if(line != null){ totalTemp = Integer.parseInt(line); }
      else{ ReqResLog("DFS Results was empty"); return; }
      // Create a temp int to figure out when we are past variables we already read in
      int temp = 0;
      line = dfsResults.readLine();
      // While the file is not empty read in results and make sure they make sense
      while(line != null){
        // If temp pass total number of variables something is wrong
        if(temp > totalTemp){ ReqResLog("Something is off about currentVar"); }
        // If temp is less than this.currentVar, then it should already be in the filesToVar. Make sure it is
        if(temp < this.currentVar){
          if(!this.filesToVar.get(temp).equalsIgnoreCase(line)) {
            ReqResLog("Something is off about filesToVar");
          }
        }else { // Add new variables to list
          this.filesToVar.add(line);
        }
        // Increment so loop continues (correctly)
        temp++;
        line = dfsResults.readLine();
      }
      // Update current count of variable
      this.currentVar = totalTemp;
      // Close file object
      dfsResults.close();
    } catch (Exception e){
      ReqResLog("An error occurred inside getCurrentDFSRequireResults (CodeGenerator.java)\n");
      ReqResLog(e.toString());
    }
  }
  public void storeCurrentDFSRequireResults(){
    PrintWriter dfsResults = (PrintWriter) openFile("write",this.dfsResultFilename,false);
    try {
      dfsResults.print(String.valueOf(currentVar));
      dfsResults.print("\n");
      for(int i=0;i<this.filesToVar.size();i++) {
        dfsResults.print(this.filesToVar.get(i).replace("\\\\","\\").replace("\\","/"));
        dfsResults.print("\n");
      }
    } catch (Exception e){
      ReqResLog("An error occurred inside storeCurrentDFSRequireResults (CodeGenerator.java)\n");
      ReqResLog(e.toString());
    } finally {
      dfsResults.close();
    }
  }
  public Object openFile(String mode, String filename, boolean append){
    Object file = null;
    if(mode.equalsIgnoreCase("write") || mode.equalsIgnoreCase("w")){
      try {
        file = new PrintWriter(new BufferedWriter(new FileWriter(filename, append)));
      } catch (Exception e) {
        String out = "An error occurred inside openFile (CodeGenerator.java)\n" +
                "\t While trying to open a file to write to\n";
        ReqResLog(out);
        ReqResLog(e.toString());
      }
    }else if(mode.equalsIgnoreCase("open") || mode.equalsIgnoreCase("o")){
      try {
        file = new BufferedReader(new FileReader(filename));
      }catch (Exception e) {
        String out = "An error occurred inside openFile (CodeGenerator.java)\n" +
                "\t While trying to open a file to read from\n";
        ReqResLog(out);
        ReqResLog(e.toString());
      }
    }
    return file;
  }
  // This will take in options and initialize all the values
  private String sourceNodeCode = null;
  private String nodePref = null;
  public void initRequireResolver(CompilerOptions options){
    // Assign values to Global Variables
    initGlobals(options);
    // Reset the log?
    boolean logReset = options.getResetRRL();
    initReqResLog(logReset);
    initDFSRR(logReset);// TODO add own boolean?

    // Any important or missed varialbes need to be checked here
    if(this.sourceNodeCode == null) {
      ReqResLog("Please enter the absolute path to the source code of Nodejs."+
              "\n[Important] Unless you are me the code will not work without this.\n");
      this.sourceNodeCode = "D:\\Sefcom\\NodeJS_code\\node-master";
    }
  }
  private String langOut = "ECMASCRIPT_2015";
  private String compLevel = "WHITESPACE_ONLY";//"SIMPLE";//"WHITESPACE_ONLY";
  private boolean resolveNJSSC = false;
  public void initGlobals(CompilerOptions options){
    // Used for log location
    this.reqreslogloc = options.getReqResLog();
    this.dfsResultFilename = options.getDFSLog();
    this.sourceNodeCode = options.getNJSSource();
    this.nodePref = options.getNodePref();
    this.resolveNJSSC = options.getNJSSBoolean();
    //this.langOut = options.getLanguageOut();
    //this.compLevel = options.getCompLevel(); // TODO complevel
    /* TODO
       Get Path of this JAR
       Get working directory
       Get Java (var)
    */
  }
}