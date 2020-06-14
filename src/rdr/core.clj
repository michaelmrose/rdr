(ns rdr.core
  (:require
   [clojure.java.shell :as ex]
   [clojure.string :as string]
   [rdr.utils :refer :all]
   [rdr.calibre :as calibre]
   [clojure.tools.cli :as cli]
   [me.raynes.fs :as fs]
   [hashp.core])
  (:gen-class))

(set! *warn-on-reflection* true)

(declare opts)

(def help-text
  "
  • rdr

    A handy cli interface to searching and opening books from your calibre library or recently read titles from your library.

  • Installation

    Download binaries from releases. Building from source requires oracles new AOT compiler for jvm based languages [graal](http://www.graalvm.org) Be advised that it is extremely memory hungry.

  • Usage

    • Actions

        -q [query]     => pass query with the same syntax as calibredb or calibregui accepts

        -l             => open the last book read

        -r             => filter the most recent 30 distinct books opened via rdr via rofi or dmenu

        query string   => If a string is passed in without -q -h -l -r specified it is treated as a query

        -o [file]      => open with default reader and record in recent reads if part of a calibre library

        -S [options]   => save options passed to disk

    • Options

        -p [list]      => list of formats in order of preference eg pdf,epub,mobi

        -k [number]    => number of recent reads to keep

        --port PORT

        --server URL

        --user USER

        --password PASSWORD

        The recommended usage is to pass the desired defaults with -S then omit them on future invocations.
        Example:

        rdr -S -L /path/to/library -p pdf,epub,mobi -k 10 -p 8090 --user me --password hunter2

        then 

        rdr -q query string here

  • Please Note

    If you expect this program to work while Calibre's gui is running you must enable Calibre's content server and pass in login parameters if applicable. The content server is disabled by default. You can enable it in Calibre's preferences menu. By default it will serve from localhost on port 8080 which is also the default for this program. If you haven't changed these parameters it is sufficient to simply enable the server. The reason for this limitation is technical. As this application wraps calibredb it inherits its limitations. When Calibre is running it can't access the database directly as both the Calibre UI and calibredb could in theory modify the database thus it can only work if it is able to communicate with the running Calibre content server.

  • Example

    example video https://www.youtube.com/watch?v=RuWe0uhzrXE&

  • Limitations
    Rdr doesn't actually support remote calibre servers yet as it presently just uses this feature to work around the fact that calibredb wont work locally without talking to the calibre content server.  It could probably be trivially expanded to fetch and then display remote books in the future.

  • License

    GPL 3.0 or later.
    Copyright © 2019 Michael Rose

  ")

(defn print-help "Print help info" []
  (println help-text))

(defn format-book-data [book]
  (str (:title book) " by " (:authors book)))

(defn list-recent-reads! []
  (let [recent (get-configuration-file-path "recent")]
    (map read-string (filter not-empty (string/split-lines (slurp recent))))))

(defn save-book-to-recent-reads!
  "saves book info map to ~/.config/booksclj/recent.edn keeping only the most recent n entries 
   where n is either 30 or the value defined by --keep"
  [book]
  (if-let* [recent-config-file (get-configuration-file-path "recent")
            deduped (distinct-by #(select-keys % [:id :library])  (flatten [book (list-recent-reads!)]))
            keep (Integer/parseInt (:keep opts))
            combined (take keep deduped)]
           (spit recent-config-file (string/join "\n" combined))
           (println "saving to recent list")))

(defn open-ebook!
  "Pass book to save-book-to-recent-reads!
   then open the most preferred format defined by -p configuration"
  [book command]
  (if-let* [preferred (calibre/select-preferred-ebook-format book opts)]
           (do (future (save-book-to-recent-reads! book))
               (future (:out (ex/sh command preferred))))))

(defn open-ebook-file! [file command]
  (if-let [book (calibre/filename-to-metadata file opts)]
    (open-ebook! book command) (ex/sh command file)))

(defn open-last-book
  "Open most recent entry from recent reads"
  [command]
  (if-let* [book (first (list-recent-reads!))
            fixed (calibre/correct-ebook-metadata-if-database-changed book opts)]
           (open-ebook! fixed command)))

(defn pick-from-recent-reads
  "Read recent reads and use pick-from-selection to open"
  [select-fn command]
  (if-let* [recent (list-recent-reads!)
            sel (select-by recent select-fn format-book-data)
            fixed (calibre/correct-ebook-metadata-if-database-changed sel opts)]
           (open-ebook! fixed command)))

(defn query-and-open [query select-fn command]
  (if-let* [res (calibre/query-string-to-vector-of-maps query opts)
            sel (select-by res select-fn format-book-data)]
           (open-ebook! sel command)))

(defn query-or-open
  "If argument is a file open it otherwise treat it as a query."
  [value select-fn command]
  (if (fs/file? value)
    (open-ebook-file! value command)
    (query-and-open value select-fn command)))

(defn print-results-of-query [query]
  (doall (map println (map format-book-data
                           (calibre/query-string-to-vector-of-maps query opts)))))
(defn print-raw-result-data [query]
  (doall (map println (calibre/query-string-to-vector-of-maps query opts))))

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
   [nil "--print"]
   [nil "--printraw"]
   [nil "--port PORT"]
   [nil "--server URL"]
   [nil "--user USER"]
   [nil "--password PASSWORD"]
   ["-S" "--save"]])

(def default-options
  {:keep 30
   :preferred [".pdf" ".epub" ".mobi"]
   :server "http://localhost"
   :port 8080})

(defn -main
  "Parse arguments and decide what action to take."
  [& args]
  (let [command "xdg-open"
        parsed (cli/parse-opts args cli-options)
        arguments (string/join " " (:arguments parsed))]

    (def opts (merge default-options (get-saved-configuration) (:options parsed)))

    (if (:save opts) (save-configuration! opts))
    (cond
      (:help opts) (print-help)
      (:printraw opts)(print-raw-result-data arguments)
      (:print opts) (print-results-of-query arguments)
      (:recent opts) (pick-from-recent-reads rofi-select command)
      (:last opts) (open-last-book command)
      (:open opts) (open-ebook-file! arguments command)
      (:query opts) (query-and-open arguments rofi-select command)
      :else (query-and-open arguments rofi-select command)))

  ;; process takes several seconds to properly terminate if we don't exit manually
  ;; yet obviously we don't want to kill the repl every time we run test main


  (if-not (is-in-repl?)
    (shutdown-agents)))
