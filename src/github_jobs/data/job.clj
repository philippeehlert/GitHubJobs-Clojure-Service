(ns github-jobs.data.job
  (:require [schema.core :as s]
            [github-jobs.model.job :as model-job]
            [datomic.api :as d]))

(defn get-jobs!
  [conn
   {:keys [title
           category
           github-id]}]
  (let [query-base {:query '{:find  [[(pull ?job [*]) ...]]
                             :in    [$]
                             :where [[?job :job/id _]]}
                    :args  [(d/db conn)]}]
    (cond-> query-base

            title
            (->
              (update-in [:query :in] conj '?title)
              (update-in [:query :where] conj '[?job :job/title ?title])
              (update-in [:args] conj title))

            category
            (->
              (update-in [:query :in] conj '?category)
              (update-in [:query :where] conj '[?job :job/category ?category])
              (update-in [:args] conj category))

            github-id
            (->
              (update-in [:query :in] conj '?github-id)
              (update-in [:query :where] conj '[?job :job/github-id ?github-id])
              (update-in [:args] conj github-id))

            true d/query)))

(s/defn insert-job!
  [conn
   job :- model-job/NewDto]
  (d/transact conn [job]))

(s/defn find-job!
  [conn
   github-id]
  (let [jobs-founded (get-jobs! conn {:github-id github-id})]
    (if (not-empty jobs-founded)
      (first jobs-founded)
      (throw (IndexOutOfBoundsException.
               (format "This github id: %s was not founded!" github-id))))))

(s/defn add-job-categories!
  [conn
   github-id :- s/Uuid
   categories :- [s/Str]]
  (for [category categories]
    (d/transact conn [[:db/add [:job/github-id github-id] :job/category category]])))

(defn ^:private update-job-cas-datoms!
  [conn
   github-id
   old-datom
   datom-key
   new-datom]
  (d/transact conn [[:db/cas [:job/github-id github-id] datom-key old-datom new-datom]]))

(defn ^:private mapper-update-job
  [old-job conn github-id]
  (fn [[key value]]
    (let [old-value (get old-job key)]
      (if-not (vector? value)
        (update-job-cas-datoms! conn github-id old-value key value)
        (add-job-categories! conn github-id value)))))

(s/defn update-job!
  [conn
   github-id :- s/Uuid
   new-job :- model-job/UpdateDto]
  (-> (find-job! conn github-id)
      (mapper-update-job conn github-id)
      (mapv new-job)))

(s/defn retract-job!
  [conn
   github-id :- s/Uuid]
  (find-job! conn github-id)
  (d/transact conn [[:db/retractEntity [:job/github-id github-id]]]))
