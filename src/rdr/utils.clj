(ns rdr.utils
  (:require
   [clojure.java.shell :as ex]
   [clojure.string :as string]
   [me.raynes.fs :as fs]
   [pandect.algo.adler32 :refer :all])
  (:gen-class))

(set! *warn-on-reflection* true)

(defn seek
  "Returns first item from coll for which (pred item) returns true.
   Returns nil if no such item is present, or the not-found value if supplied."
  {:added  "1.9" ; note, this was never accepted into clojure core
   :static true}
  ([pred coll] (seek pred coll nil))
  ([pred coll not-found]
   (reduce (fn [_ x]
             (if (pred x)
               (reduced x)
               not-found))
           not-found coll)))

(defn if-empty-then-nil [val]
  (if (empty? val) nil val))

;;TODO make passing in the desired app a thing

(defn binary-exists? [name]
  (= 0 (:exit (ex/sh "which" name))))

(defn invoke-rofi-or-dmenu [in]
  (cond
    (binary-exists? "rofi")
    (ex/sh "rofi" "-dmenu" "-i" "-m" "-1" :in in)
    (binary-exists? "dmenu")
    (ex/sh "dmenu" "-i" "-l" "10" :in in)
    :else (throw (Exception. "Please install rofi or dmenu"))))

(defn rofi-select [strings]
  (let [c (count strings)]
    (cond
      (= c 0) nil
      (= c 1) (first strings)
      :else (->> (string/join "\n" strings)
                 (ex/sh "echo" "-e")
                 :out
                 (invoke-rofi-or-dmenu)
                 :out
                 (string/trim-newline)
                 (if-empty-then-nil)))))

(defn ensure-directory-exists [path]
  (if-not (fs/exists? path)
    (fs/mkdir path)))

(defn ensure-file-exists [path]
  (if-not (fs/exists? path) (fs/touch path)))

(defn get-configuration-file-path [name]
  (let [config-dir  (str (System/getenv "HOME") "/.config/rdr/")
        config-file (str config-dir name)]
    (ensure-directory-exists config-dir)
    (ensure-file-exists config-file)
    config-file))

(defn current-stack-trace []
  (.getStackTrace (Thread/currentThread)))

(defn is-repl-stack-element [^java.lang.StackTraceElement stack-element]
  (and (= "clojure.main$repl" (.getClassName  stack-element))
       (= "doInvoke"          (.getMethodName stack-element))))

(defn is-in-repl? []
  (some is-repl-stack-element (current-stack-trace)))

(defmacro if-let*
  "Multiple binding version of if-let"
  ([bindings then]
   `(if-let* ~bindings ~then nil))
  ([bindings then else]
   #_(when (seq bindings)
       (assert-all (vector? bindings) "a vector for its binding"
                   (even? (count bindings)) "exactly even forms in binding vector"))
   (if (seq bindings)
     `(if-let [~(first bindings) ~(second bindings)]
        (if-let* ~(vec (drop 2 bindings)) ~then ~else)
        ~else)
     then)))

(defn pgrep [expr]
  (= 0 (:exit (ex/sh "pgrep" expr))))
(defn vector-contains?
  "Runs .contains on a clojure.lang.Persistentvector annotating it to avoid a reflection warning"
  [^clojure.lang.PersistentVector v value]
  (.contains v value))

(defn checksum-file [path]
  (adler32-file path))

(defn save-configuration
  "Save options like keep library path and desired reader to a configuation file so that they
   may be omitted in the future. Obviously the save key shouldnt itself be saved."
  [options]
  (spit (get-configuration-file-path "settings") (select-keys options [:keep :preferred])))

(defn get-saved-configuration []
  "If saved settings don't exist return an empty map."
  (let [contents (slurp (get-configuration-file-path "settings"))]
    (if (not (empty? contents))
      (read-string contents)
      {})))

;; from  https://github.com/weavejester/medley/blob/master/src/medley/core.cljc
;; check to see if I ought to just require this library

(defn ensure-path-string-ends-in-slash [s]
  (if (string/ends-with? s "/")
    s (str s "/")))

(defn distinct-by
  "Returns a lazy sequence of the elements of coll, removing any elements that
  return duplicate values when passed to a function f."
  ([f]
   (fn [rf]
     (let [seen (volatile! #{})]
       (fn
         ([] (rf))
         ([result] (rf result))
         ([result x]
          (let [fx (f x)]
            (if (contains? @seen fx)
              result
              (do (vswap! seen conj fx)
                  (rf result x)))))))))
  ([f coll]
   (let [step (fn step [xs seen]
                (lazy-seq
                 ((fn [[x :as xs] seen]
                    (when-let [s (seq xs)]
                      (let [fx (f x)]
                        (if (contains? seen fx)
                          (recur (rest s) seen)
                          (cons x (step (rest s) (conj seen fx)))))))
                  xs seen)))]
     (step coll #{}))))

(defn notify [message]
  (ex/sh "notify-send" "-t" "5000" message))
