// Should load json file
try{
  var test2 = require("./fileTypes/jsonTest");
  console.log(test2.msg());
} catch (err){
  console.log(test2.msg);
  console.log(require("./fileTypes/jsonTest").msg);
}