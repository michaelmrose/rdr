(ns rdr.calibre
  (:require
   [clojure.java.shell :as ex]
   [clojure.string :as string]
   [me.raynes.fs :as fs]
   [clojure.data.json :as json]
   [rdr.utils :refer :all]
   [swiss.arrows :refer :all])
  (:gen-class))

(set! *warn-on-reflection* true)

(declare calibre-running?)

(defn get-library-path! [options]
  (let [calibre-config (str (System/getenv "HOME") "/.config/calibre/global.py.json")
        active (str (get (json/read-str (slurp calibre-config)) "library_path") "/")]
    (or (:library options) active)))

(defn get-user-option [options]
  (if-let [user (:user options)]
    (str "--username=" user)
    ""))

(defn get-password-option [options]
  (if-let [password (:password options)]
    (str "--password=" password)
    ""))

(defn get-library-option [options]
  (let [path  (or (:library options) (get-library-path! options))]
    (if (calibre-running?)
      (str "--library=" (:server options) ":" (:port options) "#" (last (string/split path #"/")))
      (str "--library=" path))))

;; Calibre wont allow local reading of the library metadata when gui app is open for_machine
;; reasons of consistency thus you must communicate with the process over an interface that
;; is also used for remote communications. Over this interface you can't get the true path
;; to formats for proposed reasons of security.  To work around this shell munging is used.

;; This may seem fragile however calibre is commited to a consistent file system structure
;; /library-root/authors/title (id)/files and approach was suggested by calibre dev.
;; This is to say that given an ebooks id will always be inside parens before the last / in
;; the the files path and all files will be after the final /.  Each dir will contain in
;; addition to the ebooks an image file for the cover which will always be a jpg and a
;; metadata file ending in opf. These must be filtered out.

(defn id-to-formats [id options]
  "Given an id return formats vector containing paths to books matching id."
  (->> (:out (ex/sh "sh" "-c" (str "ls " (get-library-path! options) "*/*\\(" id  "\\)/*")))
       (string/split-lines)
       ;;need to avoid listing cover files or calibre metadata which appears to be .opf
       ;;I think any image files are always converted to jpg. Hopefully this is consistent.
       ;; (remove #(re-matches #".+\.opf|.+\.jpg" %))
       (remove #(re-matches #"(?:.+\.)(?:(?:jpg)|(?:opf))$" %))
       (filter identity)
       (into [])))

(defn fix-book-formats [book options]
  "Use id-to-formats to fix an ebook maps formats vectors which given a remote query will
   contain the format type instead of the actual local path of the file required."
  (merge book {:formats (id-to-formats (:id book) options)
               :library (get-library-path! options)}))

(defn filename-to-id-string [f options]
  "Given a predictable path this will return the id of a book or nil if the book isn't in the calibre
   libraries path."
  (let [book-in-library-pattern (re-pattern (str (get-library-path! options) ".+/.+\\(([0-9]+)\\)/.+.{1,9}$"))]
    (if-let [id (second (re-matches book-in-library-pattern f))]
      id)))

(defn calibre-running? []
  (pgrep "GUIPool|calibre"))

(defn db-changed-since-last-visit? [options]
  (let [db (str (get-library-path! options) "metadata.db")
        checksum (checksum-file db)
        checksum-file (get-configuration-file-path (str "checksum" (string/replace (string/replace db "/" "-") ".db" ".sum")))
        oldsum (slurp checksum-file)]
    (spit checksum-file checksum)
    (not= checksum oldsum)))

(defn format-query
  "Calibre specifies criteria for queries like so criteria:value with the value potentially proceeding over multiple space separated words
  and terminated on end of string or the next criteria. If we pass in a query with criteria defined pass it to calibre unchanged. If we receive
  a plain string we will return a modified query string which will serve to return a more natural result. Calibre by default searches all fields
  including an oft voluminous comment field which can contain multiple paragraphs of text. For example if an author is compared in comments text
  to William Shakespeare that book will be returned in a plain search for the text Shakespeare. This is rarely desired. Instead for a plain query
  we will return only those books wherein every word was contained in either authors titles or tags. The resulting query which is hard to reproduce in a comment is simply
  as depicted in any-of below surrounded by parens and joined by and."

  [query-string]
  (letfn [(any-of [s] (str "(" "authors:" s  " or " "title:" s " or " "tags:" s ")"))]
    (if (string/includes? query-string ":")
      query-string
      (->> (string/split query-string #" ")
           (map #(any-of %))
           (interpose "and")
           (string/join " ")))))

(defn query-string-to-vector-of-maps
  "Formats a query to calibredb and collects and processes json results
   and returns a vector of maps."
  [query options]
  (let [formatted-query (format-query query)
        library (get-library-option options)
        user (get-user-option options)
        pw (get-password-option options)
        fields "--fields=title,authors,formats"
        query-vector ["calibredb" user pw "list" fields  "-s" formatted-query "--for-machine" library]]
    (-<>
     (try
       (shelly query-vector)
       (catch Exception e
         "This is the string returned when the calibre content server is not running."
         (if (string/includes? e "URLError: <urlopen error [Errno 111] Connection refused>")
           (throw (ex-info "Connection to Calibre Content Server Refused Is It Running?" {:type :network}))
           (throw e))))

     (json/read-str :key-fn keyword)
     (mapv #(fix-book-formats % options) <>))))

(defn filename-to-metadata [f options]
  "Gets the id of a book based on the predictable path structure used by Calibre and searches the
   metadata associated with that id."
  (let [id (filename-to-id-string (.getPath ^java.io.File (fs/absolute f)) options)]
    (first (query-string-to-vector-of-maps (str "id:" id) options))))

(defn correct-ebook-metadata-if-database-changed [book options]
  "If the db has been updated since last visit update metadata. Needed because book metadata
   may be read from the recent file instead of directly from Calibre."
  (if-not (db-changed-since-last-visit? options)
    book
    (filename-to-metadata (first (:formats book)) options)))

(defn select-preferred-ebook-format [book options]
  "Given a book map and a vector of preferred formats in order of preference
   select the most desired format available"
  (if-let* [formats (:formats book)
            ^clojure.lang.LazySeq available (map fs/extension formats)
            preferred (first (filter #(.contains available %) (:preferred options)))
            desired (first (filter #(string/ends-with? % preferred) formats))]
           desired
           (first (:formats book))))
