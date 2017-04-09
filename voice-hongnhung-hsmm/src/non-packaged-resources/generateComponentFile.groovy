import groovy.xml.*
import com.twmacinta.util.MD5

def zipFile = new File(project.build.directory, "${project.build.finalName}.zip")
def zipFileHash = MD5.asHex(MD5.getHash(zipFile))
def builder = new StreamingMarkupBuilder()
def xml = builder.bind {
	'marytts-install'(xmlns: 'http://mary.dfki.de/installer') {
		voice(gender: "female", locale: "vi", name: project.properties.voiceName, type: "marytts.htsengine.HMMVoice", version: project.version) {
			delegate.description project.description
			license(href: project.licenses[0]?.url)
			'package'(filename: zipFile.name, md5sum: zipFileHash, size: zipFile.size()) {
				location(folder: true, href: "http://mary.dfki.de/download/$project.version/")
			}
			files "lib/${project.build.finalName}.jar"
			depends(language: "vi", version: project.version)
		}
	}
}
def xmlFile = new File(project.build.directory, "$project.build.finalName-component.xml")
xmlFile.text = XmlUtil.serialize(xml)
