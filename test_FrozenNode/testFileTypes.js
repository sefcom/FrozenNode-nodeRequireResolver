// Should load js file
try{
  var test = require("./fileTypes/simpleTest");
  console.log(test.msg());
} catch (err){
  console.log(test.msg);
}

// Should load json file
try{
  var test2 = require("./fileTypes/simpleTest2");
  console.log(test2.msg());
} catch (err){
  console.log(test2.msg);
}

// Should load node file
try{
  var test3 = require("./fileTypes/simpleTest3");
  console.log(test3.msg());
} catch (err){
  console.log(test3.msg);
}

// Also need to test directories
// Directories have two conditions
// package.json
// index.<valid extension>