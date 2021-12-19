import { setOnConnectionStateChangeCallback, setOnMessageCallback} from './BLEPeripheralEventsHandler'

var BLEPeripheral = require('./BLEPeripheral');

module.exports = { BLEPeripheral, setOnConnectionStateChangeCallback, setOnMessageCallback};