// Should load js file
var test = require("./fileTypes/simpleTest");
console.log(test.msg);

// Should load json file
var test2 = require("./fileTypes/simpleTest2");
console.log(test2.msg);

// Should load from "cache" JS
var test3 = require("./fileTypes/simpleTest");
console.log(test3.msg);

// Should load from "cache" JSON
var test4 = require("./fileTypes/simpleTest2");
console.log(test4.msg);