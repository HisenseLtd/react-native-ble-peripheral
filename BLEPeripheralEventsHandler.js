var { DeviceEventEmitter } = require('react-native');

export function setOnMessageCallback(cb) {
  DeviceEventEmitter.addListener('onMessage', cb);
}

export function setOnConnectionStateChangeCallback(cb) {
  DeviceEventEmitter.addListener('ConnectionStateChange', cb);
}
