package com.apigen.domain;

/** Format de la réponse renvoyée par la passerelle pour un endpoint INBOUND. */
public enum ResponseFormat {
    /** Enveloppe JSON habituelle : runId, correlationId, status, data. */
    JSON,
    /** Texte brut (text/plain). */
    TEXT,
    /** Document XML. */
    XML,
    /** Fichier produit par le pipeline, téléchargé en pièce jointe. */
    FILE,
    /** Contenu binaire (base64 du pipeline ou octets téléversés). */
    BINARY,
    /** Redirection HTTP 302. */
    REDIRECT
}
