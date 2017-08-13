(ns name-bazaar.server.effects
  (:require
    [cljs.core.async :refer [<! >! chan]]
    [district0x.server.effects :as d0x-effects]
    [district0x.server.state :as state]
    [name-bazaar.server.contracts-api.used-by-factories :as used-by-factories]
    [cljs.nodejs :as nodejs]
    [name-bazaar.server.contracts-api.ens :as ens])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def namehash (aget (nodejs/require "eth-ens-namehash") "hash"))

(def default-deploy-opts
  {:from-index 0
   :contracts-file-namespace 'name-bazaar.shared.smart-contracts
   :contracts-file-path "/src/name_bazaar/shared/smart_contracts.cljs"})

(def library-placeholders
  {:offering-library "__OfferingLibrary.sol:OfferingLibrary___"
   :buy-now-offering-library "__BuyNowOfferingLibrary.sol:BuyNowOffe__"
   :auction-offering-library "__AuctionOfferingLibrary.sol:AuctionOf__"})

(defn deploy-ens! [server-state-atom default-opts]
  (d0x-effects/deploy-smart-contract! server-state-atom (merge default-opts
                                                               {:contract-key :ens})))

(defn deploy-registrar! [server-state-atom default-opts]
  (d0x-effects/deploy-smart-contract! server-state-atom
                                      (merge default-opts
                                             {:contract-key :registrar
                                              :args [(state/contract-address @server-state-atom :ens)
                                                     (namehash "eth")]})))

(defn deploy-offering-registry! [server-state-atom default-opts]
  (d0x-effects/deploy-smart-contract! server-state-atom (merge default-opts
                                                               {:contract-key :offering-registry})))

(defn deploy-offering-requests! [server-state-atom default-opts]
  (d0x-effects/deploy-smart-contract! server-state-atom (merge default-opts
                                                               {:contract-key :offering-requests})))

(defn deploy-offering-library! [server-state-atom default-opts]
  (d0x-effects/deploy-smart-contract! server-state-atom (merge default-opts
                                                               {:contract-key :offering-library})))

(defn deploy-buy-now-library! [server-state-atom default-opts]
  (d0x-effects/deploy-smart-contract! server-state-atom (merge default-opts
                                                               {:contract-key :buy-now-offering-library
                                                                :library-placeholders (select-keys library-placeholders
                                                                                                   [:offering-library])})))

(defn deploy-auction-library! [server-state-atom default-opts]
  (d0x-effects/deploy-smart-contract! server-state-atom (merge default-opts
                                                               {:contract-key :auction-offering-library
                                                                :library-placeholders (select-keys library-placeholders
                                                                                                   [:offering-library])})))

(defn deploy-buy-now-factory! [server-state-atom default-opts {:keys [:offering-factory/emergency-multisig]}]
  (d0x-effects/deploy-smart-contract! server-state-atom (merge default-opts
                                                               {:contract-key :buy-now-offering-factory
                                                                :library-placeholders (select-keys library-placeholders
                                                                                                   [:offering-library
                                                                                                    :buy-now-offering-library])
                                                                :args [(state/contract-address @server-state-atom :registrar)
                                                                       (state/contract-address @server-state-atom :offering-registry)
                                                                       (state/contract-address @server-state-atom :offering-requests)
                                                                       emergency-multisig]})))

(defn deploy-auction-factory! [server-state-atom default-opts {:keys [:offering-factory/emergency-multisig]}]
  (d0x-effects/deploy-smart-contract! server-state-atom (merge default-opts
                                                               {:contract-key :auction-offering-factory
                                                                :library-placeholders (select-keys library-placeholders
                                                                                                   [:offering-library
                                                                                                    :auction-offering-library])
                                                                :args [(state/contract-address @server-state-atom :registrar)
                                                                       (state/contract-address @server-state-atom :offering-registry)
                                                                       (state/contract-address @server-state-atom :offering-requests)
                                                                       emergency-multisig]})))

(defn deploy-smart-contracts! [server-state-atom]
  (let [ch (chan)]
    (go
      (<! (deploy-ens! server-state-atom default-deploy-opts))
      (<! (deploy-registrar! server-state-atom default-deploy-opts))

      (<! (ens/set-subnode-owner! @server-state-atom
                                  {:ens.record/label "eth"
                                   :ens.record/node ""
                                   :ens.record/owner (state/contract-address @server-state-atom :registrar)}))

      (<! (deploy-offering-registry! server-state-atom default-deploy-opts))
      (<! (deploy-offering-requests! server-state-atom default-deploy-opts))
      (<! (deploy-offering-library! server-state-atom default-deploy-opts))
      (<! (deploy-buy-now-library! server-state-atom default-deploy-opts))
      (<! (deploy-auction-library! server-state-atom default-deploy-opts))
      (<! (deploy-buy-now-factory! server-state-atom default-deploy-opts {:offering-factory/emergency-multisig
                                                                          (state/active-address @server-state-atom)}))
      (<! (deploy-auction-factory! server-state-atom default-deploy-opts {:offering-factory/emergency-multisig
                                                                          (state/active-address @server-state-atom)}))

      (<! (used-by-factories/set-factories! @server-state-atom {:contract-key :offering-registry}))
      (<! (used-by-factories/set-factories! @server-state-atom {:contract-key :offering-requests}))

      (d0x-effects/store-smart-contracts! (:smart-contracts @server-state-atom)
                                          {:file-path (:contracts-file-path default-deploy-opts)
                                           :namespace (:contracts-file-namespace default-deploy-opts)})

      (>! ch server-state-atom))
    ch))
