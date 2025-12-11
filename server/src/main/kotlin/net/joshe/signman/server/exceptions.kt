package net.joshe.signman.server

class ConfigurationException(msg: String) : Exception(msg)

// Keep this class name in sync with the preprocessor define in linux_native.h
class DeviceException(msg: String) : Exception(msg)
