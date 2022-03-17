require 'json'

package = JSON.parse(File.read(File.join(__dir__, 'package.json')))

Pod::Spec.new do |spec|
  spec.name           = "react-native-tim"
  spec.version        = package["version"]
  spec.summary        = package["description"]
  spec.homepage       = package["homepage"]
  spec.license        = package["license"]

  spec.authors        = package["author"]
  spec.platform       = :ios, "9.0"

  spec.source         = { :git => "https://github.com/yuezonglun/react-native-tim.git", :tag => "#{spec.version}" }
  spec.source_files   = "ios/*.{h,m}"

  spec.dependency "React"
  spec.dependency "TXIMSDK_iOS", "~> 4.6.51"

end
