//var x = require("./loopVar1.js");
var x = require("./loopVarS1");

var mult = function(a,b){
	return a*b;
}

exports.msg1 = function(x,y){
	console.log("multiplying S3");
	return mult(x,y); // MSG FROM VAR3
}
exports.msg2 = function(c,d){
	try{
		console.log("adding S3");
		return x.msg1(c,d); // MSG FROM VAR1
	}catch(e){
		return "loopVar3.js does not have the message from loopVar1.js";
	}
};
exports.msg3 = function(e,f){
	try{
		console.log("subtracting S3");
		return x.msg2(e,f); // MSG FROM VAR2
	}catch(e){
		return "loopVar3.js does not have the message from loopVar2.js";
	}
};
try{
  console.log("This was in LoopVar3.js "+x.msg1(0,1));
}catch(e){
	console.log("loopVar1 was likely required/called first");
}

// This results in "TypeError: x.msg is not a function" in the function that calls the first called in loopVar. IE L2V calls L3V, so if L3V is called 1st from LV then L2V will error
/*var temp = "LoopVar 3 was called"+"\n"+x.msg();
exports.msg = () => temp;*/


//exports.msg = () => "LoopVar 3 was called"+"\n"+x.msg(); // This results in "RangeError: Maximum call stack size exceeded"


// This results in a sort of infinite loop. This makes sense because they call next.
/*var x = require("./loopVar1.js");
exports.log = function(){
	console.log(x.msg());
	x.log();
};*/