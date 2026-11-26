/*
 * Proxy du studio Angular vers le backend Spring Boot.
 *
 * Le chemin de contexte global (server.servlet.context-path) peut être modifié depuis
 * le studio et est écrit dans config/runtime.properties. Le backend et le frontend
 * ne sont pas toujours lancés depuis le même dossier : au lieu de deviner, on
 * détecte le préfixe réellement appliqué par le backend en interrogeant son endpoint
 * de santé (/actuator/health) avec et sans préfixe, dès le démarrage du proxy.
 *
 * En cas de 404 sur la connexion, une nouvelle détection est lancée et l'écran de
 * connexion réessaie automatiquement une fois.
 */
const fs = require('fs');
const path = require('path');
const http = require('http');
const https = require('https');

const TARGET = process.env.APIGEN_BACKEND || 'http://localhost:8081';
const HEALTH = '/actuator/health';

function normalize(value) {
  if (!value || value === '/') {
    return '';
  }
  return '/' + String(value).replace(/^\/+|\/+$/g, '');
}

/** Valeur déclarée dans le fichier externe (indication de départ, pas une vérité). */
function declaredContextPath() {
  if (process.env.APIGEN_CONTEXT_PATH) {
    return normalize(process.env.APIGEN_CONTEXT_PATH);
  }
  const candidates = [
    process.env.APIGEN_RUNTIME_CONFIG,
    path.resolve(__dirname, '../config/runtime.properties'),
    path.resolve(__dirname, '../backend/config/runtime.properties'),
    path.resolve(__dirname, 'config/runtime.properties')
  ].filter(Boolean);
  for (const file of candidates) {
    try {
      const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/);
      const line = lines.find((entry) => /^\s*server\.servlet\.context-path\s*=/i.test(entry));
      if (line) {
        let value = line.split('=').slice(1).join('=').trim();
        value = value.split(/\s+#/)[0].trim();
        return normalize(value);
      }
    } catch {
      // Fichier absent ou illisible : on essaie l'emplacement suivant.
    }
  }
  return '';
}

/** true si le backend répond sur cette URL (401/403 inclus : il est bien joignable). */
function reachable(url) {
  return new Promise((resolve) => {
    let request;
    try {
      const client = url.startsWith('https:') ? https : http;
      request = client.get(url, { timeout: 1500 }, (response) => {
        response.resume();
        resolve(response.statusCode !== 404 && response.statusCode < 500);
      });
    } catch {
      resolve(false);
      return;
    }
    request.on('timeout', () => {
      request.destroy();
      resolve(false);
    });
    request.on('error', () => resolve(false));
  });
}

let contextPath = declaredContextPath();

async function detectContextPath() {
  const declared = contextPath;
  const [plainOk, prefixedOk] = await Promise.all([
    reachable(TARGET + HEALTH),
    declared ? reachable(TARGET + declared + HEALTH) : Promise.resolve(false)
  ]);
  if (plainOk && !prefixedOk) {
    contextPath = '';
  } else if (prefixedOk && !plainOk) {
    contextPath = declared;
  }
  console.log('[api-gen] proxy : backend ' + TARGET + ', préfixe actif = '
    + (contextPath || '(aucun)')
    + (contextPath && contextPath !== declared ? ' (déclaré : ' + declared + ')' : ''));
}

detectContextPath();

function rewrite(targetPath, req) {
  const forced = req && req.headers && req.headers['x-apigen-context-path'];
  const prefix = typeof forced === 'string' ? normalize(forced) : contextPath;
  return prefix ? prefix + targetPath : targetPath;
}

function proxyOptions() {
  return {
    target: TARGET,
    secure: false,
    changeOrigin: true,
    pathRewrite: rewrite,
    onProxyRes: (proxyRes, req) => {
      proxyRes.headers['x-apigen-context'] = contextPath || '(none)';
      // Le backend peut avoir été redémarré avec un autre contexte depuis le dernier
      // contrôle : un 404 sur /api/auth/ déclenche une nouvelle détection.
      if (proxyRes.statusCode === 404 && req.url && req.url.startsWith('/api/auth/')) {
        contextPath = declaredContextPath();
        detectContextPath();
      }
    },
    onError: (_err, _req, res) => {
      if (res && !res.headersSent) {
        res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({ message: 'Backend indisponible sur ' + TARGET }));
      }
    }
  };
}

module.exports = {
  '/api': proxyOptions(),
  '/actuator': proxyOptions()
};
