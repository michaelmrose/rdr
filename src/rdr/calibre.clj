(ns rdr.calibre
  (:require
   [clojure.java.shell :as ex]
   [clojure.string :as string]
   [me.raynes.fs :as fs]
   [clojure.data.json :as json]
   [rdr.utils :refer :all]
   [swiss.arrows :refer :all]
   )
  (:gen-class))

(set! *warn-on-reflection* true)

(defn get-library-path []
  (let [calibre-config (str (System/getenv "HOME") "/.config/calibre/global.py.json")]
    (str (get (json/read-str(slurp calibre-config)) "library_path") "/")))


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


(defn id-to-formats [id]
  "Given an id return formats vector containing paths to books matching id."
  (->>(:out (ex/sh "sh" "-c" (str "ls " (get-library-path) "*/*\\(" id  "\\)/*")))
      (string/split-lines)
      ;;need to avoid listing cover files or calibre metadata which appears to be .opf
      ;;I think any image files are always converted to jpg. Hopefully this is consistent.
      ;; (remove #(re-matches #".+\.opf|.+\.jpg" %))
      (remove #(re-matches #"(?:.+\.)(?:(?:jpg)|(?:opf))$" %))
      (filter identity)
      (into []))) 

(defn fix-book-formats [book]
  "Use id-to-formats to fix an ebook maps formats vectors which given a remote query will
   contain the format type instead of the actual local path of the file required."
  (assoc book :formats (id-to-formats (:id book))))

(defn filename-to-id-string [f]
  "Given a predictable path this will return the id of a book or nil if the book isn't in the calibre
   libraries path."
  (let [book-in-library-pattern (re-pattern (str (get-library-path) ".+/.+\\(([0-9]+)\\)/.+.{1,9}$"))]
    (if-let [id (second(re-matches book-in-library-pattern f))]
      id)))

(defn calibre-running? []
  (pgrep "GUIPool|calibre" ))

(defn db-changed-since-last-visit? []
  (let [db (str (get-library-path) "metadata.db")
        checksum (checksum-file db)
        checksum-file (get-configuration-file-path "checksum")
        oldsum (slurp checksum-file)
        ]
    (spit checksum-file checksum)
    (not= checksum oldsum)))


(defn return-remote-library-segment []
  (if (calibre-running?) "--with-library=http://localhost:8080" ""))

(defn format-calibredb-query "Take the full QUERY passed to our app and format it for processing with calibredb. Will need --for-machine to get a json output. Will need to.
   Will need --with-library=http://localhost:8080 if calibre-running? returns true"
  [query]
  ["calibredb" "list" "--fields" "title,authors,formats" "-s" query "--for-machine" (return-remote-library-segment)])

(defn query-string-to-vector-of-maps 
  "Uses format-calibredb-query to format query, collects and processes json results
   and returns a vector of maps"
  [query]
  (-<> (:out (apply ex/sh (format-calibredb-query query)))
       (json/read-str :key-fn keyword)
       (mapv fix-book-formats <>)
       ))

(defn list-all-books []
  (query-string-to-vector-of-maps "*"))

(defn filename-to-metadata [f]
  (let [id (filename-to-id-string f)]
    (query-string-to-vector-of-maps (str "id:" id))))

(defn select-preferred-ebook-format [book preferred-formats]
  "Given a book map and a vector of preferred formats in order of preference
   select the most desired format available"
  (if-let* [formats (:formats book)
            ^clojure.lang.LazySeq available (map fs/extension formats)
            preferred (first(filter #(.contains available %) preferred-formats))
            desired (first(filter #(string/ends-with? % preferred) formats))
            ]
    desired
    (first (:formats book))))


