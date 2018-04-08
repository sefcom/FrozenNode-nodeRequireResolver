var x = require("./loopVarS2.js");

var add = function(x,y){
	return x+y;
}

exports.msg1 = function(x,y){
	console.log("adding S1");
	return add(x,y); // MSG FROM VAR1
}
exports.msg2 = function(c,d){
	try{
		console.log("subtracting S1");
		return x.msg1(c,d); // MSG FROM VAR2
	}catch(e){
		return "loopVar1.js does not have the message from loopVar2.js";
	}
};
exports.msg3 = function(e,f){
	try{
		console.log("multiplying S1");
		return x.msg2(e,f); // MSG FROM VAR3
	}catch(e){
		return "loopVar1.js does not have the message from loopVar3.js";
	}
};
try{
  console.log("This was in LoopVar1.js "+x.msg1(0,1));
}catch(e){
	console.log("loopVar2 was likely required/called first");
}

// This results in "TypeError: x.msg is not a function"
/*var temp = "LoopVar 1 was called"+"\n"+x.msg();
exports.msg = () => temp;*/


//exports.msg = () => "LoopVar 1 was called"+"\n"+x.msg(); // This results in "RangeError: Maximum call stack size exceeded"


// This results in a sort of infinite loop
/*var x = require("./loopVar2.js");
exports.log = function(){
	console.log(x.msg());
	x.log();
};*/