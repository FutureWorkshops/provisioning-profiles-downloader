def parse(path)
  xml_plist = `security cms -D -i '#{path}'`
  return nil unless xml_plist != nil

  plist = Plist.parse_xml(xml_plist)
  return nil unless (plist || []).count > 5
  plist
end

def download_profile(devportal_username, devportal_password, devportal_teamID, profile_name, destination_folder)

  puts "Running with username/teamid: #{devportal_username}/#{devportal_teamID}"
  puts "Looking for a valid profile named: #{profile_name}"

  Spaceship::Portal.login(devportal_username, devportal_password)
  Spaceship::Portal.client.team_id = devportal_teamID

  all_profiles = Spaceship::Portal.provisioning_profile.all
  profile_index = all_profiles.find_index { |p| p.name == profile_name }
  if profile_index == nil
    STDERR.puts "ERROR: Can't find a provisioning profile named \"#{profile_name}\""
    exit -1
  end

  profile = all_profiles[profile_index]
  unless profile.valid?
    STDERR.puts "ERROR: Found a matching provisioning profile but it's expired!"
    exit -2
  end

  puts "Found valid provisioning profile with name \"#{profile.name}\": "
  puts "  Type:      #{profile.class.pretty_type}"
  puts "  Bundle ID: #{profile.app.bundle_id}"
  puts "  UUID:      #{profile.uuid}"
  puts "  Expires:   #{profile.expires}"


  output_profile_name = "[#{profile.class.pretty_type}]#{profile.name}(#{profile.app.bundle_id}).mobileprovision"
  FileUtils.mkdir_p(destination_folder) unless File.directory?(destination_folder)
  downloaded_profile_path = File.join(destination_folder, output_profile_name)
  File.open(downloaded_profile_path, "wb") do |f|
    f.write(profile.download)
  end

  FileUtils.touch(File.join(destination_folder, "#{profile.uuid}.uuid"))

  profile_path = File.expand_path("~") + "/Library/MobileDevice/Provisioning Profiles/"
  profile_filename = profile.uuid + ".mobileprovision"
  if profile_filename == nil
    STDERR.puts "ERROR: Error parsing provisioning profile at path '#{path}'"
    exit -3
  end

  destination = profile_path + profile_filename

  if File.exist?(destination)
    puts "Removing previous provisioning profile at path: #{destination}"
    FileUtils.rm(destination)
  end

  puts "Installing provisioning profile: #{output_profile_name} -> #{destination}"
  FileUtils.mkdir_p(profile_path) unless File.directory?(profile_path)
  FileUtils.copy(downloaded_profile_path, destination)
end

##################################
## Main

devportal_username = ARGV[0]
devportal_password = ARGV[1]
devportal_teamID = ARGV[2]
prov_profile_names = ARGV[3]
destination_folder = ARGV[4]

unless ARGV.length == 5
  puts "Usage: #{__FILE__} [username] [password] [teamID] [profile_names] [destination_folder]"
  puts "       profile_names should be separated by a comma"
  exit -10
end

puts "Installing required libraries"
unless system("gem install --user-install spaceship plist", {:out =>"/dev/null", :err =>"/dev/null"})
  puts "#{$?}"
  STDERR.puts "Unable to install required ruby libraries"
  exit -11
end

gem 'plist'
gem 'spaceship'

require 'spaceship'
require 'fileutils'
require 'plist'
prov_profile_names = prov_profile_names.strip
prov_profile_names.split(',').each { |profile_name| download_profile(devportal_username, devportal_password, devportal_teamID, profile_name.strip, destination_folder) }