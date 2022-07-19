case class WebStats (
  // the total number of (unique) files found
  numFiles: Int,
                    
  // the total number of (unique) file extensions
  numExts: Int,
                    
  // a map storing the the total number of files for each extension
  extCounts: Map[String, Int],
                    
  // the total number of words in all html files combined, excluding html tags,
  // attributes and components
  totalWordCount: Long
                    )                  
