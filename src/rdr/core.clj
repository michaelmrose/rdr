(ns rdr.core
  (:require
   [clojure.java.shell :as ex]
   [clojure.string :as string]
   [clojure.data.json :as json]
   [rdr.utils :refer :all]
   [rdr.calibre :as calibre]
   )
  (:gen-class))

(set! *warn-on-reflection* true)

(def help-text
  "Usage:
  -q [query] => pass query with the same syntax as calibredb or calibregui accepts
  -r         => filter the most recent 30 distinct books opened via rdr via rofi or dmenu
  -l         => open the last book read
  -o [file]  => open with zathura and record in recent reads if part of a calibre library

  See proposed help text for a more complete cli to be implemented ")

(def proposed-help-text
  "Usage:
  -q or --query [query]  => pass query to calibres library, if multiple items are returned
                          resulting titles will be narrowed with rofi then the resulting
                          choice will be opened with the preferred format and reader

  Query can consist of a simple string of text whereupon the program will search the library for books with matching strings in the tags,authors,title fields. Alternatively criteria can be specified explicitly with -q -c criteria:query. If so there is multiple criteria specified one can assume there is an and between criteria. And and or can also be explicitly be specified. In addition short forms of criteria can be specified. Example

  t for title
  tg or tag for tags
  author or a for author

  values can be prefaced with = to avoid for example fiction matching non-fiction. Furthermore some values have implicit terms. Example scifi will match science fiction and fiction wont match non fiction. This is accomplished by substituting a single term for a more complex one. Example books -q -c tg:fiction could see tg:fiction expanded and  replaced with the result being equivalent to books -q -c tags:fiction and not tags:non-fiction and not tags:nonfiction.

  These synonyms for criteria and implicit terms can be specified in a configuration file so that you may add your own.

  -o or --open [file] => Opens file and saves to recent reads if within calibre library
  -l or --last        => Opens last read book
  -r or --recent      => use rofi to pick from recent reads
  -p or --preferred [comma separated list of formats in order of preference]

  Example: -p pdf,epub,mobi

  defaults to pdf,epub,mobi

  If a given book has multiple formats available the preferred format will be opened.
  A format that isn't listed implicitly comes after those that are.


  -R or --reader [comma separated list of commands] or map of formats to applications. If not specified prompt for values to be saved for later invocations.

  Example -R zathura,ebook-viewer
        -R zathura
        -R epub=ebook-viewer,pdf=zathura,else=ebook-viewer

  -L or --library [path] => the path to your calibre library, optional

  ------------
  -k or --keep the number of recent reads to keep fifo, defaults to 30
  -S [options] arguments to -p -R -L -k to save as the default for future invocations

  -D epub,pdf,mobi sets books as the default handler of the listed types. defaults to pdf,epub,mobi


  The recommended path is to first run books -S with your desired options

  example: books -S -D -k 30 -R zathura -p pdf,epub,mobi

  then run books -q whatever you please

  If you make the included books desktop file the default handler for ebooks it will properly record your recent reads even if you open the file manually or with calibres gui interface.
  "
  )

(defn return-ebook-reader-command 
  "Read the type of file found at books path or paths and then compare it to a map from user configuration to    
   determine what reader command to use. map could be passed quoted at cli or a single application name
   passed with -r ex: -r zathura or -r '{epub somereader pdf zathura else foobar}'This could ultimately be part of a configuration file"
  [book]
  "zathura"
  )

(defn save-book-to-recent-reads 
  "saves book info map to ~/.config/booksclj/recent.edn keeping only the most recent n entries 
   where n is either 30 or the value defined by --keep"
  [book n]
  (let [recent (get-configuration-file-path "recent")
        current (string/split-lines(slurp recent))
        combined (string/join "\n"(take n (distinct(flatten [(str book) current]))))]
    (spit recent combined))
  (println "saving to recent list")
  )


(defn open-ebook 
  "If books path is within the calibre library path pass book to save-book-to-recent-reads
   then open the most preferred format with the preferred reader defined by -p and -r or configuration"
  [book & options]
  (save-book-to-recent-reads book 30)
  (ex/sh (return-ebook-reader-command book) (calibre/select-preferred-ebook-format book [".pdf" ".epub"] ))
  )

(defn open-ebook-file [file]
  (ex/sh (return-ebook-reader-command file) file)
  (if-let [book (calibre/filename-to-metadata file)]
    (save-book-to-recent-reads book 30)
    (ex/sh (return-ebook-reader-command file) file)
    ))

(defn print-book-details [book]
  (let [title (:title book)
        authors (:authors book)]
    (str title " by " authors)))

(defn select-from-books-by-title-and-open [books]
  (if-let* [^clojure.lang.PersistentVector titles (mapv print-book-details books)
            choice (rofi-select titles)
            ndx (.indexOf titles choice)
            book (nth books ndx) 
            ]
    (open-ebook book)))

(defn save-configuration 
  "save options like keep library path and desired reader to a configuation file so that they
   may be omitted in the future."
  [options]
  )


(defn list-recent-reads []
  (let [recent (get-configuration-file-path "recent")]
    (map read-string (string/split-lines(slurp recent)))))


(defn open-last-book 
  "Open most recent entry from recent reads"
  []
  (open-ebook (first (list-recent-reads))))

(defn pick-from-recent-reads 
  "Read recent reads and use pick-from-selection to open"
  []
  (select-from-books-by-title-and-open (list-recent-reads)))

(defn print-help "Print help info" []
  (println (slurp "usage.txt")))

(defn query-and-open [query]
  (select-from-books-by-title-and-open (calibre/query-string-to-vector-of-maps query) ))

(defn -main
  "Parse arguments and decide what action to take."
  [& args]
  (let [op (first args)
        payload (string/join " " (rest args))]
    (condp = op
      "-q" (query-and-open payload)
      "-r" (pick-from-recent-reads)
      "-l" (open-last-book)
      "-o" (open-ebook-file payload)
      "-h" (print-help)
      ))

  ;; process takes several seconds to properly terminate if we don't exit manually
  ;; yet obviously we don't want to kill the repl every time we run test main
  (if-not (is-in-repl?) 
    (shutdown-agents)))
