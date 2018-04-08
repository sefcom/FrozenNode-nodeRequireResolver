var x = require("./loopVarS3.js");

var sub = function(a,b){
	return a-b;
}

exports.msg1 = function(x,y){
	console.log("subtracting S2");
	return sub(x,y); // MSG FROM VAR2
}
exports.msg2 = function(c,d){
	try{
		console.log("multiplying S2");
		return x.msg1(c,d); // MSG FROM VAR3
	}catch(e){
		return "loopVar2.js does not have the message from loopVar3.js";
	}
};
exports.msg3 = function(e,f){
	try{
		console.log("adding S2");
		return x.msg2(e,f); // MSG FROM VAR1
	}catch(e){
		return "loopVar2.js does not have the message from loopVar1.js";
	}
};
try{
  console.log("This was in LoopVar2.js "+x.msg1(0,1));
}catch(e){
	console.log("loopVar3 was likely required/called first");
}

// This results in "TypeError: x.msg is not a function"
/*var temp = "LoopVar 2 was called"+"\n"+x.msg();
exports.msg = () => temp;*/


//exports.msg = () => "LoopVar 2 was called"+"\n"+x.msg(); // This results in "RangeError: Maximum call stack size exceeded"


// This results in a sort of infinite loop
/*var x = require("./loopVar3.js");
exports.log = function(){
	console.log(x.msg());
	x.log();
};*/