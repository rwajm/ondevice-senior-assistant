package com.k2j3.seniorassistant.device.ai.llm

class InvalidOutputException(val output: String) : Exception("not an allowed function call")
