// Should load js file
try{
  var test = require("./directoryTests/packageJsonToJS");
  console.log(test.msg());
} catch (err){
  console.log(test.msg);
}

// Should load json file
try{
  var test2 = require("./directoryTests/packageJsonToJSON");
  console.log(test2.msg());
} catch (err){
  console.log(test2.msg);
}

// Should load node file
try{
  var test3 = require("./directoryTests/packageJsonToNode");
  console.log(test3.msg());
} catch (err){
  console.log(test3.msg);
}

console.log(require.resolve("./directoryTests/packageJsonToJS"));
console.log(require.resolve("./directoryTests/packageJsonToJSON"));
console.log(require.resolve("./directoryTests/packageJsonToNode"));

// Should work
try{
  var test = require("./directoryTests/IndexJS");
  console.log(test.msg());
} catch (err){
  console.log(test.msg);
}
try{
  var test = require("./directoryTests/IndexJSON");
  console.log(test.msg());
} catch (err){
  console.log(test.msg);
}
try{
  var test = require("./directoryTests/IndexNode");
  console.log(test.msg());
} catch (err){
  console.log(test.msg);
}

console.log(require.resolve("./directoryTests/IndexJS"));
console.log(require.resolve("./directoryTests/IndexJSON"));
console.log(require.resolve("./directoryTests/IndexNode"));

try{
  var test = require("./directoryTests/packageNodeIndexJS");
  console.log(test.msg());
} catch (err){
  console.log(test.msg);
}

console.log("\nThis is the end of the directories that should work\n");

// package without a valid main
try{
  var test1 = require("./directoryTests/packageJson");
} catch (err){
  console.log(err)
}
// Single file not index and without a package.json
try{
  var test1 = require("./directoryTests/js");
} catch (err){
  console.log(err)
}