import BLEPeripheral from './BLEPeripheral';
var { NativeEventEmitter } = require('react-native');

const eventEmitter = new NativeEventEmitter(BLEPeripheral);

export function setOnMessageCallback(cb) {
  eventEmitter.addListener('onMessage', cb);
}

export function setOnConnectionStateChangeCallback(cb) {
  eventEmitter.addListener('ConnectionStateChange', cb);
}
