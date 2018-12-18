//  Created by Eskel on 12/12/2018.

import Foundation
import CoreBluetooth

@objc class BLEPeripheral: NSObject, CBPeripheralManagerDelegate {
    var advertising: Bool = false
    var peripheralManager: CBPeripheralManager!
    var servicesMap = Dictionary<String, CBMutableService>()

    override init() {
        super.init()
        peripheralManager = CBPeripheralManager(delegate: self, queue: nil, options: nil)
        print("BLEPeripheral initialized, advertising: \(advertising)")
    }

    @objc func isAdvertising(_ resolve: RCTPromiseResolveBlock) {
        resolve(advertising)
        print("called isAdvertising")
    }

    @objc func addService(_ uuid: String, _ primary: Bool) {
        let serviceUUID = CBUUID(string: uuid)
        if(servicesMap.keys.contains(uuid) != true){ servicesMap[uuid] = CBMutableService(type: serviceUUID, primary: primary) }
        print("called addService")
    }

    @objc func addCharacteristicToService(_ serviceUUID: String, _ uuid: String, _ permissions: UInt, _ properties: UInt, _ data: String) {
        let characteristicUUID = CBUUID(string: uuid)
        let propertyValue = CBCharacteristicProperties(rawValue: properties)
        let permissionValue = CBAttributePermissions(rawValue: permissions)
        let byteData: Data = data.data(using: .utf8)!
        let characteristic = CBMutableCharacteristic( type: characteristicUUID, properties: propertyValue, value: byteData, permissions: permissionValue)
        servicesMap[serviceUUID]?.characteristics?.append(characteristic)
        print("called addCharacteristicToService")
    }

    @objc func start() {
        let advertisementData = [CBAdvertisementDataLocalNameKey: "Test data"]
        peripheralManager.startAdvertising(advertisementData)
        print("called start")
    }

    @objc func stop() {
        peripheralManager.stopAdvertising()
        print("called stop")
    }

    @objc func sendNotificationToDevices() {
        print("called stop")
    }

    @objc static func requiresMainQueueSetup() -> Bool {
        return false
    }

    // Private functiomns
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        print("updated state: \(peripheral.state)")
    }
    
}
