import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpBackend, HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { FrontendConfigService } from '../../core/frontend-config.service';

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <main style="max-width:800px;margin:32px auto;padding:24px">
      <h1>Configurer la plateforme</h1>
      <p>Assistant réservé à l’installateur. Le backend doit être démarré avec APIGEN_SETUP_ENABLED=true. Les API métier sont arrêtées pendant cette opération.</p>
      <p class="alert alert-warning">Utilisez HTTPS hors de votre poste local. Aucun compte DBA n’est nécessaire : faites créer la base et le compte dédié par votre administrateur. Changer de base ne transfère pas les données existantes.</p>
      <div class="field"><label>Adresse du backend (si hébergé ailleurs)</label>
        <input class="input" [(ngModel)]="backendUrl" placeholder="https://serveur:8443/apigen">
        <span class="hint">Incluez son contexte global. Ce réglage public reste dans ce navigateur ; aucun secret n’y sera enregistré.</span>
      </div>
      <button class="btn btn-secondary" [disabled]="busy()" (click)="connect()">Joindre le mode installation</button>
      @if (available()) {
        <div class="field"><label>Code temporaire d’installation <span class="required">*</span></label><input class="input" type="password" [(ngModel)]="code" autocomplete="off"><span class="hint">Copiez le code affiché dans la console du backend. Valable 30 minutes.</span></div>
        <div class="field"><label>Type de base <span class="required">*</span></label>
          <select class="select" [(ngModel)]="form.type" (ngModelChange)="changeType()">
            <option value="ORACLE">Oracle 12c minimum (19c+ recommandé)</option>
            <option value="POSTGRESQL">PostgreSQL 13 minimum</option><option value="MYSQL">MySQL 8 minimum</option>
            <option value="SQLSERVER">SQL Server 2016 minimum</option><option value="H2">H2 local — développement uniquement</option>
          </select>
        </div>
        @if (form.type !== 'H2') {
          <div class="form-row">
            <div class="field"><label>Serveur <span class="required">*</span></label><input class="input" [(ngModel)]="form.host" placeholder="localhost"></div>
            <div class="field"><label>Port <span class="required">*</span></label><input class="input" type="number" [(ngModel)]="form.port"></div>
          </div>
          @if (form.type === 'ORACLE') {
            <div class="field"><label>Mode d’adresse Oracle <span class="required">*</span></label><select class="select" [(ngModel)]="form.oracleAddressing"><option value="SERVICE">Nom de service / PDB</option><option value="SID">SID</option></select></div>
          }
          <div class="field"><label>{{ form.type === 'ORACLE' ? 'SID ou service' : 'Nom de la base' }} <span class="required">*</span></label><input class="input" [(ngModel)]="form.database"></div>
        } @else { <p>Le fichier local ./data/apigen sera ouvert ou créé. Ne pas utiliser ce choix pour une base de production partagée.</p> }
        <div class="field"><label>Utilisateur <span class="required">*</span></label><input class="input" [(ngModel)]="form.username" autocomplete="off"></div>
        <div class="field"><label>Mot de passe @if (form.type !== 'H2') { <span class="required">*</span> }</label><input class="input" type="password" [(ngModel)]="form.password" autocomplete="new-password"></div>
        <p class="hint">SQL Server utilise TLS avec validation du certificat. Les réglages TLS avancés et les politiques réseau doivent être préparés par l’exploitant. Aucun certificat n’est accepté aveuglément.</p>
        <div class="row mt-16">
          <button class="btn btn-secondary" [disabled]="busy() || saved()" (click)="action('test')">Tester la connexion et la version</button>
          <button class="btn btn-primary" [disabled]="busy() || saved()" (click)="action('save')">Vérifier et enregistrer</button>
        </div>
      }
      @if (busy()) { <p>Opération en cours…</p> }
      @if (message()) { <p role="status" class="alert alert-info">{{ message() }}</p> }
      @if (saved()) { <p>Retirez APIGEN_SETUP_ENABLED ou définissez-le à false, puis redémarrez le backend. Les tables seront préparées au démarrage normal. Vérifiez également les réglages CORS et le mot de passe admin initial avant ouverture.</p> }
      <p><a routerLink="/login">Retour à la connexion</a></p>
    </main>
  `
})
export class SetupComponent {
  private config = inject(FrontendConfigService);
  private http = new HttpClient(inject(HttpBackend)); // No Studio credentials or login redirects.
  backendUrl = this.config.apiBaseUrl();
  code = '';
  available = signal(false);
  saved = signal(false);
  busy = signal(false);
  message = signal('');
  form = { type: 'ORACLE', host: 'localhost', port: 1521, database: '', oracleAddressing: 'SERVICE', username: 'APIGEN', password: '' };
  private base(): string { return this.backendUrl.trim().replace(/\/+$/, ''); }
  changeType(): void {
    this.form.port = ({ ORACLE: 1521, POSTGRESQL: 5432, MYSQL: 3306, SQLSERVER: 1433, H2: 0 } as Record<string, number>)[this.form.type];
    this.form.username = this.form.type === 'H2' ? 'sa' : this.form.type === 'ORACLE' ? 'APIGEN' : 'apigen';
    this.form.password = ''; this.form.database = '';
  }
  connect(): void {
    if (this.base() && !/^https?:\/\//i.test(this.base())) { this.message.set('Indiquez une adresse complète http:// ou https://.'); return; }
    this.busy.set(true); this.available.set(false);
    this.http.get<any>(this.base() + '/api/setup/status').subscribe({ next: status => {
      this.busy.set(false); this.available.set(status.setup === true); this.saved.set(status.saved === true);
      this.message.set('Mode installation accessible. Renseignez le code affiché sur le serveur.');
      this.config.applyOverride({ backendUrl: this.base(), contextPath: '' });
    }, error: () => { this.busy.set(false); this.message.set('Assistant indisponible. Vérifiez l’adresse, CORS et le démarrage du backend avec APIGEN_SETUP_ENABLED=true.'); } });
  }
  action(operation: 'test' | 'save'): void {
    this.busy.set(true); this.message.set('');
    this.http.post<any>(this.base() + '/api/setup/' + operation, this.form, { headers: { 'X-Setup-Code': this.code } }).subscribe({ next: result => {
      this.busy.set(false); this.message.set(result.message);
      if (operation === 'save') { this.saved.set(true); this.form.password = ''; this.code = ''; }
    }, error: error => { this.busy.set(false); this.message.set(error?.error?.message || 'Opération impossible. Vérifiez la connexion et la console du serveur, puis réessayez.'); } });
  }
}
