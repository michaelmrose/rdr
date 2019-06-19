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

(defn get-library-option [options]
  (let [path  (or (:library options) (get-library-path! options))]
    (if (calibre-running?)
      (str "--library=http://localhost:8080/#" (last (string/split path #"/")))
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

(defn query-string-to-vector-of-maps
  "Formats a query to calibredb and collects and processes json results
   and returns a vector of maps."
  [query options]
  (let [library (get-library-option options)
        fields "--fields=title,authors,formats"
        query-vector ["calibredb" "list" fields  "-s" query "--for-machine" library]]
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
