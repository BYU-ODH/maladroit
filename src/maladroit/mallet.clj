(ns maladroit.mallet
  (:require [clojure.test :refer :all]
            [clojure.core :refer :all]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [clojure.string :as str]
            [ring.util.io :refer [piped-input-stream]])
  (:import [cc.mallet.util.*]
           [cc.mallet.types InstanceList]
           [cc.mallet.pipe
            Input2CharSequence TokenSequenceLowercase
            CharSequence2TokenSequence SerialPipes
            TokenSequenceRemoveStopwords
            TokenSequence2FeatureSequence]
           [cc.mallet.pipe.iterator FileListIterator StringArrayIterator]
           [cc.mallet.topics ParallelTopicModel]
           [java.io
            FileFilter
            PipedInputStream
            PipedOutputStream
            ByteArrayInputStream
            ByteArrayOutputStream]
           [java.util Formatter Locale]))

(def ^:dynamic *test-topic-counts*
  [5 10 15 20 25 30 35 40 50 60 70 80 90 100])
(def ^:dynamic *samples* 100)

(defn to-input-stream
  "Convert from a byte-array-output-stream to a piped-input stream"
  [byte-array-output-stream]
  (piped-input-stream
   (fn [ostream]
     (.writeTo byte-array-output-stream ostream))))

(defn easy-file-split
  "Split a file into multiple strings on a regexp, memory intensive"
  ([] (easy-file-split "./resources/docs/quad.org" #"(?m)^\* "))
  ([file regexp]
   (let [file-string (slurp file)]
     (str/split file-string regexp))))


(defn make-pipe-list
  "Creates an `InstanceList` that tokenizes and normalizes a
  list of input files. It also removes stopwords."
  ([] (make-pipe-list nil))
  ([stopwords]
   (let [remove-stopwords (TokenSequenceRemoveStopwords. false false)]
     (when stopwords
       (.addStopWords remove-stopwords stopwords))
     (InstanceList.
      (SerialPipes.
       [(Input2CharSequence. "UTF-8")
        (CharSequence2TokenSequence.
         #"\p{L}[\p{L}\p{P}]+\p{L}")
        (TokenSequenceLowercase.)
        remove-stopwords
        (TokenSequence2FeatureSequence.)])))))

(defn add-strings
  [instance-list data-array]
  (.addThruPipe
   instance-list
   (StringArrayIterator. data-array)))

(defn add-directory-files
  "Adds the files from a directory to the instance list."
  [instance-list data-dir]
  (.addThruPipe
   instance-list
   (FileListIterator.
    (.listFiles (io/file data-dir))
    (reify FileFilter
      (accept [this pathname] true))
    #"/([^/]*).txt$"
    true)))

(defn show-instance
  "Shows information about the instance."
  ([model instances n]
   (show-instance (.. model getData (get n))
                  (.getDataAlphabet instances)))
  ([data-item data-alphabet]
   (let [tokens (.. data-item instance getData)
         topics (.topicSequence data-item)
         out (Formatter. (StringBuilder.) Locale/US)]
     (dotimes [i (.size tokens)]
       (.format
        out "%s-%d "
        (object-array
         [(.lookupObject data-alphabet
                         (.getIndexAtPosition tokens i))
          (.getIndexAtPosition topics i)])))
     (str out))))

(defn make-topic-info
  "Creates a map of information about the instance and topic.
  This should pull out everything needed for the basic
  visualizations."
  ([model n topic-n]
   (make-topic-info model (.getTopicProbabilities model n)
                    n topic-n))
  ([model topic-dist n topic-n]
   (let [path (.. model getData (get n) instance
                  getName getPath)]
     {:instance-n n
      :path (str/replace path #".*/([^/]*).txt" "$1")
      :topic-n topic-n
      :distribution (aget topic-dist topic-n)})))

(defn get-topic-words
  "Returns the words and weights as they apply to the given
  topic."
  [model instances topic-n]
  (let [topic-words (.getSortedWords model)
        data-alpha (.getDataAlphabet instances)]
    (map #(vector (.lookupObject data-alpha (.getID %))
                  (.getWeight %))
         (iterator-seq (.. topic-words (get topic-n)
                           iterator)))))

(defn get-topic-words-only
  "Returns the words as they apply to the given topic."
  [model instances topic-n]
  (let [topic-words (.getSortedWords model)
        data-alpha (.getDataAlphabet instances)]
    (map #(vector (.lookupObject data-alpha (.getID %))
                  ;(.getWeight %)
                  )
         (iterator-seq (.. topic-words (get topic-n)
                           iterator)))))

(defn get-model-topic-words
  "Returns [[instance-num [word-list]]] for each instance in the model"
  [model instances & {:keys [num-keywords]
                      :or {num-keywords 5}}]
  (let [ model-words (.getSortedWords model)
        data-alpha (.getDataAlphabet instances)]
    ;; for each topic,
    (for [topic-words model-words]
      (into [] (for [w (take num-keywords topic-words)]
                 (.lookupObject data-alpha (.getID w)))))))

(defn get-topic-dists
  "Returns a list of maps containing information about the
  topics (from `make-topic-info`) for the given instance. Sorted
  in descending order by distribution."
  [model instances n]
  (let [num-topics (.numTopics model)
        topic-words (.getSortedWords model)
        topic-dist (.getTopicProbabilities model n)
        data-alpha (.getDataAlphabet instances)]
    (->> model
         .numTopics
         range
         (map (partial make-topic-info model topic-dist n))
         (sort-by :distribution)
         reverse)))

(defn get-all-dists
  "Returns all maps from `get-topics-dists` for all instances."
  [model instances]
  (map (partial get-topic-dists model instances)
       (range (.size instances))))

(defn show-instance-dist
  "Shows information about the distributions for the instance."
  [model instances instance-n n]
  (let [out (Formatter. (StringBuilder.) Locale/US)
        num-topics (.numTopics model)
        topic-words (.getSortedWords model)
        topic-dist (.getTopicProbabilities model instance-n)
        data-alpha (.getDataAlphabet instances)]
    (dotimes [topic num-topics]
      (.format out "\n%d\t%.3f\t"
               (object-array [topic (aget topic-dist topic)]))
      (doseq [pair
              (take n
                    (iterator-seq
                     (.. topic-words (get topic) iterator)))]
        (.format out "%s (%.0f) "
                 (object-array
                  [(.lookupObject data-alpha (.getID pair))
                   (.getWeight pair)]))))
    (str out)))

(defn get-topic-weights
  "Returns the weights for this topic across all instances."
  [model instances topic-n]
  (->>
   instances
   .size
   range
   (map #(make-topic-info model % topic-n))))

(defn create-instance
  "Creates a new instance with a high probability of coming from
  a given topic."
  [model instances topic-bias length]
  (let [out (StringBuilder.)
        data-alpha (.getDataAlphabet instances)
        topic-words (.getSortedWords model)]
    (doseq [pair
            (take
             length
             (iterator-seq
              (.. topic-words (get topic-bias) iterator)))]
      (.append out
               (str (.lookupObject data-alpha (.getID pair))
                    \space)))
    (str out)))

(defn train-model
  "Returns a trained model."
  ([instances] (train-model 10 4 1000 instances))
  ([num-topics num-threads num-iterations instances]
   (doto (ParallelTopicModel. num-topics 5.0 0.01) ;; changed alphasum to 5.0 to match cli
     ;; num-topics alphaSum beta
     (.addInstances instances)
     (.setOptimizeInterval 10) ;; TSA
     (.setTopicDisplay 0 0) ;; TSA need to turn off the output to std out (will go faster)
     (.setRandomSeed 1)        ; TSA
     (.setBurninPeriod 200)    ; TSA
     (.setSymmetricAlpha true)  ; TSA
     (.setNumThreads num-threads)
     (.setNumIterations num-iterations)
     (.estimate))))

(defn base-report
  [model instances]
  ;; Show the words and topics for the first instance.
  (println "Words and Topics for the First Instance\n"
           (show-instance model instances 0))
  (newline)

  ;; Estimate the topic distribution of the first instance,
  ;; given the current Gibbs state. Show the top five words with
  ;; proportions.
  (println "Topic Distribution of First Document\n"
           (show-instance-dist model instances 0 5))
  (newline)

  ;; Create a new instance with high probability of topic 0.
  (println "Create instance with high probability of topic 0.\n"
           (create-instance model instances 0 5))
  (newline))

(defn write-topic-distributions
  [model instances filename]
  (with-open [out (io/writer filename)]
    (csv/write-csv out [["instance" "topic" "distribution"]])
    (csv/write-csv
     out
     (map #(vector (:path %) (:topic-n %) (:distribution %))
          (mapcat #(get-topic-weights model instances %)
                  (range (.numTopics model)))))))

(defn write-topic-words
  [model instances filename]
  (with-open [out (io/writer filename)]
    (csv/write-csv out [["word" "weight" "topic"]])
    (csv/write-csv
     out
     (mapcat
      (fn [t]
        (take 10
              (map #(conj % t)
                   (get-topic-words model instances t))))
      (range (.numTopics model))))))

(defn generate-dump-model
  [instances num-topics num-iterations file-prefix]
  (let [model (train-model
               num-topics 8 num-iterations instances)]
    (write-topic-distributions
     model instances (str file-prefix "-dists.csv"))
    (write-topic-words
     model instances (str file-prefix "-words.csv"))
    model))

(defn inc-count
  "Increments a key's count in the hash-map m."
  [m k]
  (assoc m k (inc (m k 0))))

(defn get-top-topic
  "Returns a list of the topic(s) most likely for this
  document."
  [model instance-n]
  (let [probs (mapv vector
                    (range)
                    (.getTopicProbabilities model instance-n))
        [_ mx] (apply max-key second probs)]
    (filterv #(= (second %) mx) probs)))

(defn get-all-topics-instance
  [model instance-n]
  (mapv vector
        (range)
        (.getTopicProbabilities model instance-n)))

(defn get-all-topics
  [model instance-names]
  (->> instance-names
       .size
       range
       (map #(vector (get instance-names %) (get-all-topics-instance model %)))))

(defn get-top-topics
  "Returns a sequence of pairs of the instance ID and the top
  topic(s) for that instance."
  [model instances]
  (->>
   instances
   .size
   range
   (map #(vector % (get-top-topic model %)))))

(defn get-ambiguous
  "This returns a map of the instance IDs to their ambiguous
  topics (i.e., more than one top topic)."
  [model instances]
  (into {}
        (filter #(> (count (second %)) 1)
                (get-top-topics model instances))))

(defn train-ambiguous
  [instances size]
  (let [m (train-model size 8 50 instances)]
    (get-ambiguous m instances)))

(defn ambiguous-report
  ([instances]
   (ambiguous-report instances *test-topic-counts*))
  ([instances sizes]
   (reduce (fn [a s]
             (let [m (train-model s 8 50 instances)]
               (assoc a s (count (get-ambiguous m instances)))))
           {}
           sizes)))

(defn delta-top-instance
  "This returns the difference in the top n topic probabilities
  for an instance."
  ([model instance-n] (delta-top-instance model instance-n 2))
  ([model instance-n n]
   (let [spread (take
                 n (reverse
                    (sort
                     (.getTopicProbabilities model n))))]
     (- (first spread) (last spread)))))

(defn avg-top-spread
  "This returns the average top spread for all instances."
  ([model instances] (avg-top-spread model instances 2))
  ([model instances n]
   (let [deltas (mapv #(delta-top-instance model % n)
                      (range (.size instances)))]
     (/ (apply + deltas) (count deltas)))))

(defn model-spread-report
  [instances topic-n]
  (let [model (train-model topic-n 8 50 instances)]
    (map #(delta-top-instance model % topic-n)
         (range (.size instances)))))

(defn accum-spread
  [instances samples a s]
  (let [data (mapcat (fn [_]
                       (model-spread-report instances s))
                     (range samples))]
    (assoc a s (/ (apply + data) (count data)))))

(defn spread-report
  ([instances]
   (spread-report instances 2 *samples* *test-topic-counts*))
  ([instances n]
   (spread-report instances n *samples* *test-topic-counts*))
  ([instances n samples sizes]
   (reduce #(accum-spread instances samples %1 %2) {} sizes)))

(defn topic-counts
  "This returns a map of the number of documents in each topic."
  [model instances]
  (->>
   instances
   (get-top-topics model)
   (mapcat second)         ; Throw away instance IDs.
   (map first)             ; Throw away probabilities.
   (reduce inc-count {})))

(defn topic-keywords
  "Get topics and keywords for whole corpus"
  [& {:keys [file num-iterations num-topics num-threads num-keywords instance-list model]
      :or {file "/home/torysa/Workspace/Docs/maladroit/quad.txt"
           num-iterations 1000
           num-topics 10
           num-threads 4
           num-keywords 8}}]
  (let [string-vec (if-not instance-list
                 (-> file slurp vector into-array)
                 nil)
        the-instance-list (or instance-list
                              (make-pipe-list))
        maybe-add-strings (when-not instance-list ;; add strings if we are making a new instance list
                            (add-strings the-instance-list string-vec))
        model (or model
                 (train-model num-topics num-threads num-iterations the-instance-list))
        keywords-list (get-model-topic-words model instance-list :num-keywords num-keywords)
        ;; keywords-list (into []
        ;;                     (for [n (range num-topics)]
        ;;                       (take num-keywords (get-topic-words-only model the-instance-list n))))
        ]
    (map-indexed vector keywords-list)))

(defn get-topic-name [s]
  (let [dlen 20
        slen (count s)]
  (cond
    (= 0 (count s)) "Blank"
    (< slen dlen) (subs s 0 slen)
    :defaul (subs s 0 dlen))))

(defn process-file
  ;; emulate: --output-topic-keys --output-doc-topics
  ;; #"(?m)^\* "
  [& {:keys [file regexp num-iterations num-topics num-threads stopwords]
      :or {file "/home/torysa/Workspace/Docs/maladroit/quad.txt"
           regexp #"(?m)^\* "
           num-iterations 1000
           num-topics 10
           num-threads 4
           num-keywords 8
           stopwords (into-array String [])}}]
  (let [overall-instance-list (make-pipe-list stopwords)
        topics-strings (-> file (easy-file-split regexp))
        instance-names (into [] (for [s topics-strings] (get-topic-name s)))
        topics-strings-array (into-array topics-strings)
        _populate-list (add-strings overall-instance-list topics-strings-array)
        _ (println "Strings added\n\n")
        model (train-model num-topics num-threads num-iterations overall-instance-list)
        _ (println "model trained")
        tk (topic-keywords :file file
                           :model model
                           :instance-list overall-instance-list
                           :num-iterations num-iterations
                           :num-topics num-topics
                           :num-threads num-threads)
        _ (println "topic-keywords successful")
        dt (get-all-topics model instance-names)
        ]
    {:topics-keys tk
     :doc-topics dt}))

(defn flatten-for-tsv
  [data]
  (for [d data] (flatten d)))

(defn to-tsv
  "Outputs topics-keys to a TSV input stream as received from process-file"
  [data]
  (let [bos (ByteArrayOutputStream.)]
    (with-open [os bos
                w (io/writer os)]
      (csv/write-csv w (flatten-for-tsv data) :separator \tab))
    bos)) 

(defn to-disk [tsv]
  (let [out-file "/home/torysa/temp/topickeys.csv"]
    (with-open [os (io/output-stream out-file)] (.writeTo tsv os))))
