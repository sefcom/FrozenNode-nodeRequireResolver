console.log("LoopVar was called");
var x = require("./loopVarS1");
var z = require("./loopVarS3");
var y = require("./loopVarS2");

// Loop 1
console.log("A > S > M");
console.log(x.msg1(2,3));
console.log(x.msg2(3,4));
console.log(x.msg3(4,5));
// Loop 2
console.log("S > M > A");
console.log(y.msg1(2,3));
console.log(y.msg2(3,4));
console.log(y.msg3(4,5));
// Loop 3
console.log("M > A > S");
console.log(z.msg1(2,3));
console.log(z.msg2(3,4));
console.log(z.msg3(4,5));