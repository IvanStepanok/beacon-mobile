# Adds the compiled Core ML model (DamageClassifier.mlmodelc) to the iosApp target's
# Copy-Bundle-Resources phase, idempotently. Run from the iosApp/ dir after compiling the model:
#   xcrun coremlcompiler compile <DamageClassifier.mlpackage> iosApp/
#   ruby add_model_to_xcode.rb
require 'xcodeproj'

PROJECT = File.join(__dir__, 'iosApp.xcodeproj')
# Path relative to the project dir (main_group path) — the inner source folder next to the Swift files.
MODEL_REL = 'iosApp/DamageClassifier.mlmodelc'

proj = Xcodeproj::Project.open(PROJECT)
target = proj.targets.find { |t| t.name == 'iosApp' } or abort('iosApp target not found')

if proj.files.any? { |f| f.path&.include?('DamageClassifier.mlmodelc') }
  puts 'DamageClassifier.mlmodelc already referenced — nothing to do'
  exit 0
end

ref = proj.main_group.new_reference(MODEL_REL)
target.resources_build_phase.add_file_reference(ref, true)
proj.save
puts "added #{MODEL_REL} to #{target.name} resources"
