def printClassPath(classLoader) {
  println "$classLoader"
  classLoader.getURLs().each {url->
     println "- ${url.toString()}"
  }
  if (classLoader.parent) {
     printClassPath(classLoader.parent)
  }
}
printClassPath this.class.classLoader
