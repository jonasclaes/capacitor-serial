import Foundation

@objc public class Serial: NSObject {
    @objc public func echo(_ value: String) -> String {
        print(value)
        return value
    }
}
