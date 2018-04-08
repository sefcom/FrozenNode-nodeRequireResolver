#include <node.h>

using v8::FunctionCallbackInfo;
using v8::Isolate;
using v8::Local;
using v8::Object;
using v8::String;
using v8::Value;

void message(const FunctionCallbackInfo<Value>& args) {
	Isolate* isolate = args.GetIsolate();
	args.GetReturnValue().Set(String::NewFromUtf8(isolate,"simpleTest.node was loaded"));
}

void Init(Local<Object> exports, Local<Object> module){
	NODE_SET_METHOD(exports, "msg", message);
}

NODE_MODULE(NODE_GYP_MODULE_NAME, Init)