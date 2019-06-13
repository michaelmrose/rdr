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


;; TODO: Make fetching a different library than the active one feasible when calibre is running.
;; Its entirely possible to specify a library here when talking to a remote calibre instance its just a matter
;; of passing an id not a path. Maybe instead of reading some py file it would be better to MAKE the user pass in a path
;; or id. Can we derive the id from the path? I think it will always be the last part of the path string.  What if the users library
;; is always a remote library running on a different system? Does it make sense to eventually support that? How should that be specified?
;; In that case should we fetch the book from the remote library then open? Should such files be cached in case they are fetched repeatedly?


(defn get-library-path! [options]
  (let [calibre-config (str (System/getenv "HOME") "/.config/calibre/global.py.json")
        active (str (get (json/read-str (slurp calibre-config)) "library_path") "/")
        from-options (:library options)]
    (if (calibre-running?) active
        (or from-options active))))

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
  (let [library (cond (calibre-running?) "--with-library=http://localhost:8080"
                      (:library options) (str "--library=" (:library options))
                      :else "")
        fields "--fields=title,authors,formats"
        query-vector ["calibredb" "list" fields  "-s" query "--for-machine" library]]
    (-<> (:out (apply ex/sh query-vector))
         (json/read-str :key-fn keyword)
         (mapv #(fix-book-formats % options) <>))))

(defn filename-to-metadata [f options]
  (let [id (filename-to-id-string (.getPath ^java.io.File (fs/absolute f)) options)]
    (first (query-string-to-vector-of-maps (str "id:" id) options))))

(defn select-preferred-ebook-format [book preferred-formats]
  "Given a book map and a vector of preferred formats in order of preference
   select the most desired format available"
  (if-let* [formats (:formats book)
            ^clojure.lang.LazySeq available (map fs/extension formats)
            preferred (first (filter #(.contains available %) preferred-formats))
            desired (first (filter #(string/ends-with? % preferred) formats))]
           desired
           (first (:formats book))))

(defn correct-ebook-metadata-if-database-changed [book options]
  (if-not (db-changed-since-last-visit? options)
    book
    (filename-to-metadata (first (:formats book)) options)))
