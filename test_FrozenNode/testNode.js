// Should load node file
try{
  var test3 = require("./fileTypes/simpleTest3");
  console.log(test3.msg());
} catch (err){
  console.log(test3.msg);
}