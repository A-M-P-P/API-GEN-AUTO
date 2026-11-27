import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ToastService } from '../../core/toast.service';

interface Profile { id?: number; organizationId?: number; code: string; name: string; active: boolean; mode: string; apiIds: string; pipeline: string; }
@Component({
  standalone: true,
  imports: [FormsModule],
  template: `
    <h2>Accès des consommateurs</h2>
    <p>Chaque système possède sa vérification et ses API autorisées. Les consommateurs ne se connectent pas au Studio.</p>
    <div class="alert alert-warning">Toutes les API entrantes exigent désormais un token consommateur. Les anciens JWT Studio et clés statiques ne sont plus acceptés. Les API sans organisation restent bloquées.</div>
    <div class="field"><label>Organisation <span class="required">*</span></label>
      <select class="select" [ngModel]="orgId" (ngModelChange)="selectOrganization($event)">
        <option [ngValue]="null">Choisir une organisation</option>
        @for (org of organizations(); track org.id) { <option [ngValue]="org.id">{{ org.name }}</option> }
      </select>
    </div>
    @if (orgId) {
      <div class="row mt-16">
        @for (profile of profiles(); track profile.id) {
          <button class="btn btn-secondary" (click)="edit(profile)">{{ profile.name }} {{ profile.active ? '' : '(désactivé)' }}</button>
        }
        <button class="btn btn-primary" (click)="newProfile()">+ Ajouter un système</button>
      </div>
      <div class="card card-pad mt-16">
        <h3>Profil du système</h3>
        <div class="form-row">
          <div class="field"><label>Code du système <span class="required">*</span></label><input class="input" [(ngModel)]="form.code" [disabled]="!!form.id" placeholder="systeme-a"></div>
          <div class="field"><label>Nom du système <span class="required">*</span></label><input class="input" [(ngModel)]="form.name" placeholder="Système de gestion A"></div>
        </div>
        <div class="field"><label>Vérification des identifiants <span class="required">*</span></label>
          <select class="select" [(ngModel)]="form.mode"><option value="LOCAL">Identifiant et secret gérés ici</option><option value="PIPELINE">Règles du système existant (pipeline Oracle ou HTTP)</option></select>
        </div>
        <h4>API autorisées pour ce système <span class="required">* avant activation</span></h4>
        @for (api of apis(); track api.id) {
          <label style="display:block"><input type="checkbox" [checked]="selectedApis.includes(api.id)" (change)="toggleApi(api.id, $any($event.target).checked)"> {{ api.name }} ({{ api.code }})</label>
        }
        @if (!apis().length) { <p>Créez ou rattachez d’abord des API à cette organisation.</p> }
        @if (form.mode === 'PIPELINE') {
          <div class="alert alert-info">Configuration avancée par votre équipe technique : le pipeline doit vérifier les identifiants ET les droits du système. Son résultat final doit contenir authenticated: true, subject et apiIds. Sans ce résultat, aucun token n’est émis. Ne placez aucun secret dans ce texte : utilisez les connexions et secrets d’organisation.</div>
          <div class="field"><label>Pipeline d’authentification <span class="required">* avant activation</span></label>
            <textarea class="textarea code" rows="12" [(ngModel)]="form.pipeline" spellcheck="false"></textarea>
          </div>
          <p class="hint">Les identifiants reçus sont disponibles dans request.body. Références : {{ credentialExample }}. Le résultat peut autoriser seulement une partie des API cochées. Aucune étape LOG, écriture SQL ou fichier n’est autorisée.</p>
        }
        <label><input type="checkbox" [(ngModel)]="form.active"> Activer ce système</label>
        <p class="hint">L’enregistrement révoque les sessions existantes du système. Les profils créés par défaut restent désactivés jusqu’à leur configuration.</p>
        <button class="btn btn-primary" [disabled]="busy()" (click)="save()">Enregistrer le profil</button>
        @if (form.id) {
          <button class="btn btn-ghost" [disabled]="busy()" (click)="revoke()">Révoquer tous les tokens de ce système</button>
          <h4 class="mt-16">Sessions des consommateurs</h4>
          <button class="btn btn-secondary" (click)="loadSessions()">Actualiser les sessions</button>
          @for (session of sessions(); track session.id) {
            <div class="row mt-8"><span>{{ session.subject }} — {{ session.revoked ? 'Révoquée' : 'Expiration : ' + session.accessExpires }}</span>
              @if (!session.revoked) { <button class="btn btn-ghost" [disabled]="busy()" (click)="revokeSession(session.id)">Révoquer cette session</button> }
            </div>
          }
          <h4 class="mt-16">Endpoints disponibles</h4>
          <p>Connexion (POST) : <code>{{ tokenPath() }}/token</code></p>
          <p>Renouvellement (POST) : <code>{{ tokenPath() }}/refresh</code></p>
          <p class="hint">Préfixez ces chemins par l’adresse et le contexte global du backend. Durée du token : 24 h maximum. Renouvellement rotatif pendant 7 jours maximum, puis nouvelle connexion.</p>
          @if (form.mode === 'LOCAL') {
            <h4>Consommateurs de ce système</h4>
            <div class="field"><label>Nouvel identifiant <span class="required">*</span></label><input class="input" [(ngModel)]="clientName" placeholder="application-dgi"></div>
            <button class="btn btn-secondary" [disabled]="busy()" (click)="addClient()">Créer et afficher son secret une seule fois</button>
            @if (secret()) {
              <div class="alert alert-warning">Copiez ce secret dans un coffre-fort. Il ne sera plus affiché : <code>{{ secret() }}</code>
                <button class="btn btn-ghost" (click)="secret.set('')">Masquer</button>
              </div>
            }
            @for (client of clients(); track client.id) {
              <div class="row mt-8"><span>{{ client.clientId }} — {{ client.active ? 'Actif' : 'Désactivé' }}</span>
                @if (client.active) { <button class="btn btn-ghost" [disabled]="busy()" (click)="disableClient(client.id)">Désactiver et révoquer ses accès</button> }
              </div>
            }
            <p class="hint">Envoyez client_id et client_secret en JSON à l’endpoint de connexion. Pour remplacer un secret, désactivez l’ancien identifiant et créez-en un nouveau.</p>
          }
        }
      </div>
    }
  `
})
export class ConsumerAuthComponent implements OnInit {
  private http = inject(HttpClient);
  private toast = inject(ToastService);
  organizations = signal<any[]>([]);
  profiles = signal<Profile[]>([]);
  apis = signal<any[]>([]);
  clients = signal<any[]>([]);
  sessions = signal<any[]>([]);
  secret = signal('');
  busy = signal(false);
  orgId: number | null = null;
  selectedApis: number[] = [];
  clientName = '';
  credentialExample = '{{request.body.username}} et {{request.body.password}}';
  form: Profile = this.empty();
  private empty(): Profile { return { code: '', name: '', active: false, mode: 'LOCAL', apiIds: '[]', pipeline: '[]' }; }
  ngOnInit(): void {
    this.http.get<any[]>('/api/organizations').subscribe({ next: rows => this.organizations.set(rows), error: e => this.error(e) });
  }
  selectOrganization(id: number | null): void {
    this.orgId = id; this.profiles.set([]); this.apis.set([]); this.newProfile();
    if (id) {
      this.reload();
      this.http.get<any>('/api/organizations/' + id).subscribe({ next: org => this.apis.set(org.apis || []), error: e => this.error(e) });
    }
  }
  reload(): void {
    this.http.get<Profile[]>('/api/organizations/' + this.orgId + '/auth-profiles').subscribe({ next: rows => this.profiles.set(rows), error: e => this.error(e) });
  }
  newProfile(): void { this.form = this.empty(); this.selectedApis = []; this.secret.set(''); this.clients.set([]); this.sessions.set([]); this.clientName = ''; }
  edit(profile: Profile): void {
    this.form = { ...profile }; this.selectedApis = JSON.parse(profile.apiIds || '[]'); this.secret.set(''); this.loadClients(); this.loadSessions();
  }
  toggleApi(id: number, checked: boolean): void { this.selectedApis = checked ? [...this.selectedApis, id] : this.selectedApis.filter(value => value !== id); }
  save(): void {
    let pipeline: unknown;
    try { pipeline = JSON.parse(this.form.pipeline); } catch { this.toast.error('Le pipeline doit contenir une liste JSON valide.'); return; }
    const path = '/api/organizations/' + this.orgId + '/auth-profiles';
    const body = { ...this.form, apiIds: this.selectedApis, pipeline };
    this.busy.set(true);
    const request = this.form.id ? this.http.put<Profile>(path + '/' + this.form.id, body) : this.http.post<Profile>(path, body);
    request.subscribe({ next: p => { this.busy.set(false); this.edit(p); this.reload(); this.toast.success('Profil enregistré. Les anciennes sessions ont été révoquées.'); }, error: e => this.error(e) });
  }
  tokenPath(): string {
    const org = this.organizations().find(o => o.id === this.orgId);
    return '/' + (org?.code || '') + '/api/auth/' + this.form.code;
  }
  loadClients(): void {
    if (!this.form.id) return;
    this.http.get<any[]>('/api/consumer-profiles/' + this.form.id + '/clients').subscribe({ next: rows => this.clients.set(rows), error: e => this.error(e) });
  }
  addClient(): void {
    this.busy.set(true); this.secret.set('');
    this.http.post<any>('/api/consumer-profiles/' + this.form.id + '/clients', { clientId: this.clientName }).subscribe({ next: c => { this.busy.set(false); this.secret.set(c.secret); this.clientName = ''; this.loadClients(); }, error: e => this.error(e) });
  }
  disableClient(id: number): void {
    this.busy.set(true);
    this.http.delete('/api/consumer-profiles/' + this.form.id + '/clients/' + id).subscribe({ next: () => { this.busy.set(false); this.loadClients(); }, error: e => this.error(e) });
  }
  loadSessions(): void {
    if (!this.form.id) return;
    this.http.get<any[]>('/api/consumer-profiles/' + this.form.id + '/sessions').subscribe({ next: rows => this.sessions.set(rows), error: e => this.error(e) });
  }
  revokeSession(id: number): void {
    this.busy.set(true);
    this.http.delete('/api/consumer-profiles/' + this.form.id + '/sessions/' + id).subscribe({ next: () => { this.busy.set(false); this.loadSessions(); }, error: e => this.error(e) });
  }
  revoke(): void {
    this.busy.set(true);
    this.http.post('/api/consumer-profiles/' + this.form.id + '/revoke', {}).subscribe({ next: () => { this.busy.set(false); this.loadSessions(); this.toast.success('Tous les tokens de ce système ont été révoqués.'); }, error: e => this.error(e) });
  }
  private error(e: any): void { this.busy.set(false); this.toast.error(e?.error?.message || 'Action impossible. Vérifiez les champs puis réessayez.'); }
}
