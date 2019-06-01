(ns rdr.core
  (:require
   [clojure.java.shell :as ex]
   [clojure.string :as string]
   [rdr.utils :refer :all]
   [rdr.calibre :as calibre]
   [clojure.tools.cli :as cli])
  (:gen-class)
  (:refer-clojure))

(set! *warn-on-reflection* true)
(declare opts)

(def help-text
  "Usage:
  -p [list]     => list of formats in order of preference eg pdf,epub,mobi
  -S [options]  => save options to disk
  -k [number]   => number of recent reads to keep
  -q [query]    => pass query with the same syntax as calibredb or calibregui accepts
  -r            => filter the most recent 30 distinct books opened via rdr via rofi or dmenu
  -l            => open the last book read
  -o [file]     => open with default reader and record in recent reads if part of a calibre library
  a query here  => same as -q a query here")

(def answer 42)

(defn return-ebook-reader-command
  "Read the type of file found at books path or paths and then compare it to a map from user configuration to    
   determine what reader command to use. map could be passed quoted at cli or a single application name
   passed with -r ex: -r zathura or -r '{epub somereader pdf zathura else foobar}'This could ultimately be part of a configuration file"
  [book]
  ;; (let [ext (fs/extension (calibre/select-preferred-ebook-format book (:preferred opts)))]
  ;;   ext)
  "xdg-open")

(defn save-book-to-recent-reads
  "saves book info map to ~/.config/booksclj/recent.edn keeping only the most recent n entries 
   where n is either 30 or the value defined by --keep"
  [book]
  (if-let* [recent-config-file (get-configuration-file-path "recent")
            current (map read-string (string/split-lines (slurp recent-config-file)))
            combined (take (:keep opts) (distinct-by #(select-keys % [:id :library])  (flatten [book current])))]
           (spit recent-config-file (string/join "\n" combined))
           (println "saving to recent list")))

;;TODO this opens the file with xdg-open open rather than depending on an expressed preference in reader
;; This means it can't be set as the default handler else you would see an infinite series of invocations instead of
;; the app opening the book in the desired reader until this is corrected
(defn open-ebook
  "Pass book to save-book-to-recent-reads
   then open the most preferred format with the preferred reader defined by -p and -r or configuration"
  [book]
  (let [fixed (calibre/correct-ebook-metadata-if-database-changed book opts)
        command (return-ebook-reader-command fixed)
        preferred (calibre/select-preferred-ebook-format fixed (:preferred opts))]
    (future (save-book-to-recent-reads fixed))
    (future (ex/sh command preferred))))

(defn open-ebook-file [file]
  (if-let [book (calibre/filename-to-metadata file opts)]
    (open-ebook book)
    (ex/sh (return-ebook-reader-command file) file)))
(defn print-book-details [book] (let [title (:title book)
                                      authors (:authors book)]
                                  (str title " by " authors)))
(defn select-from-books-by-title [books]
  (if-let* [^clojure.lang.PersistentVector titles (mapv print-book-details books)
            choice (rofi-select titles)
            ndx (.indexOf titles choice)
            book (nth books ndx)]
           book))

(defn list-recent-reads []
  (let [recent (get-configuration-file-path "recent")]
    (map read-string (string/split-lines (slurp recent)))))

(defn open-last-book
  "Open most recent entry from recent reads"
  []
  (open-ebook (first (list-recent-reads))))

(defn pick-from-recent-reads
  "Read recent reads and use pick-from-selection to open"
  []
  (if-let* [recent (list-recent-reads)
            sel (select-from-books-by-title recent)]
           (open-ebook sel)))

(defn print-help "Print help info" []
  (println help-text))

(defn query-and-open [query]
  (if-let* [res (calibre/query-string-to-vector-of-maps query opts) sel (select-from-books-by-title res)]
           (open-ebook sel)))

(defn print-results-of-query [query]
  (doall (map println (map #(str (:title %) " by " (:authors %))
                           (calibre/query-string-to-vector-of-maps query opts)))))

(def cli-options
  [["-h" "--help"]
   ["-l" "--last"]
   ["-L" "--library PATH"
    :parse-fn ensure-path-string-ends-in-slash]
   ["-r" "--recent"]
   ["-q" "--query"]
   ["-o" "--open"]
   ["-k" "--keep NUMBER"]
   ["-p" "--preferred FORMATS"
    :parse-fn #(string/split % #",")]
   ["-P" "--print"]
   ["-S" "--save"]])

;; TODO: Impliment this properly
;; ["-R" "--reader DEFAULTS"
;;  :parse-fn read-string ]


(def default-options
  {:keep 30
   :preferred [".pdf" ".epub" ".mobi"]
   :reader {:default "xdg-open"}})

(defn -main
  "Parse arguments and decide what action to take."
  [& args]
  (let [parsed (cli/parse-opts args cli-options)
        arguments (string/join " " (:arguments parsed))]

    (def opts (merge default-options (get-saved-configuration) (:options parsed)))

    (cond
      (:help opts) (print-help)
      (:save opts) (save-configuration opts)
      (:recent opts) (pick-from-recent-reads)
      (:last opts) (open-last-book)
      (:open opts) (open-ebook-file arguments)
      (:query opts) (query-and-open arguments)
      (:print opts) (print-results-of-query arguments)
      :else (query-and-open arguments)))

  ;; process takes several seconds to properly terminate if we don't exit manually
  ;; yet obviously we don't want to kill the repl every time we run test main


  (if-not (is-in-repl?)
    (shutdown-agents)))
