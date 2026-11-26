/*
 * Proxy du studio Angular vers le backend Spring Boot.
 *
 * Le chemin de contexte global (server.servlet.context-path) peut être modifié depuis
 * le studio et est écrit dans config/runtime.properties. On le relit à chaque requête :
 * inutile de redémarrer `npm start` après avoir changé /apigen, par exemple.
 */
const fs = require('fs');
const path = require('path');

const TARGET = process.env.APIGEN_BACKEND || 'http://localhost:8081';

function normalize(value) {
  if (!value || value === '/') {
    return '';
  }
  return '/' + String(value).replace(/^\/+|\/+$/g, '');
}

function contextPath() {
  if (process.env.APIGEN_CONTEXT_PATH) {
    return normalize(process.env.APIGEN_CONTEXT_PATH);
  }
  for (const file of [
    path.resolve(__dirname, '../config/runtime.properties'),
    path.resolve(__dirname, '../backend/config/runtime.properties')
  ]) {
    try {
      const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/);
      const line = lines.find((entry) => /^\s*server\.servlet\.context-path\s*=/i.test(entry));
      if (line) {
        let value = line.split('=').slice(1).join('=').trim();
        value = value.split(/\s+#/)[0].trim();
        return normalize(value);
      }
    } catch {
      // Fichier absent : chemin de contexte vide.
    }
  }
  return '';
}

function rewrite(targetPath) {
  const prefix = contextPath();
  return prefix ? prefix + targetPath : targetPath;
}

module.exports = {
  '/api': {
    target: TARGET,
    secure: false,
    changeOrigin: true,
    pathRewrite: rewrite,
    onError: (_err, req, res) => {
      if (res && !res.headersSent) {
        res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({ message: 'Backend indisponible sur ' + TARGET }));
      }
    }
  },
  '/actuator': {
    target: TARGET,
    secure: false,
    changeOrigin: true,
    pathRewrite: rewrite
  }
};
