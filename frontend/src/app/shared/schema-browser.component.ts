import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, inject, signal } from '@angular/core';
import { ConnectionService } from '../core/connection.service';
import { ColumnInfo, DiscoveryQuery, DiscoveryResult } from '../core/models';

interface TableRef {
  schema: string;
  table: string;
}

/**
 * Explorateur de schémas / tables / colonnes d'une base cible.
 *
 * Fonctionne avec une connexion enregistrée (`connectionId`) ou avec la configuration
 * inline d'un formulaire non enregistré. Les tables sont chargées avec les schémas,
 * les colonnes ne sont lues qu'à la demande.
 */
@Component({
  selector: 'app-schema-browser',
  standalone: true,
  imports: [],
  templateUrl: './schema-browser.component.html',
  styleUrl: './schema-browser.component.scss'
})
export class SchemaBrowserComponent implements OnChanges {
  private service = inject(ConnectionService);

  @Input({ required: true }) query!: DiscoveryQuery;
  /** Permet de masquer les actions d'insertion quand l'explorateur sert uniquement à consulter. */
  @Input() selectable = true;

  @Output() tableChosen = new EventEmitter<TableRef>();

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly result = signal<DiscoveryResult | null>(null);
  readonly expanded = signal<string[]>([]);
  readonly selected = signal<TableRef | null>(null);
  readonly columns = signal<Record<string, ColumnInfo[]>>({});
  readonly loadingColumns = signal<Record<string, boolean>>({});

  private lastKey = '';

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['query'] && this.query) {
      // L'objet peut être reconstruit à chaque détection de changement : on ne relit
      // la base que si les paramètres ont réellement changé.
      const key = JSON.stringify(this.query);
      if (key !== this.lastKey) {
        this.lastKey = key;
        this.load();
      }
    }
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.columns.set({});
    this.selected.set(null);
    this.expanded.set([]);
    this.service.discover(this.query).subscribe({
      next: (result) => {
        this.result.set(result);
        this.loading.set(false);
        const first = result.schemas.find((schema) => schema.tables.length > 0);
        if (first) {
          this.expanded.set([first.name]);
        }
      },
      error: (err) => {
        this.loading.set(false);
        this.result.set(null);
        this.error.set(this.extractMessage(err));
      }
    });
  }

  toggleSchema(name: string): void {
    this.expanded.update((list) =>
      list.includes(name) ? list.filter((item) => item !== name) : [...list, name]);
  }

  isExpanded(name: string): boolean {
    return this.expanded().includes(name);
  }

  selectTable(schema: string, table: string): void {
    const key = `${schema}.${table}`;
    if (this.selected()?.schema === schema && this.selected()?.table === table) {
      this.selected.set(null);
      return;
    }
    this.selected.set({ schema, table });
    this.tableChosen.emit({ schema, table });
    if (this.columns()[key]) {
      return;
    }
    this.loadingColumns.update((map) => ({ ...map, [key]: true }));
    this.service.columns(this.query, schema, table).subscribe({
      next: (result) => {
        this.columns.update((map) => ({ ...map, [key]: result.columns }));
        this.loadingColumns.update((map) => ({ ...map, [key]: false }));
      },
      error: () => {
        this.loadingColumns.update((map) => ({ ...map, [key]: false }));
        this.columns.update((map) => ({ ...map, [key]: [] }));
      }
    });
  }

  isSelected(schema: string, table: string): boolean {
    const current = this.selected();
    return !!current && current.schema === schema && current.table === table;
  }

  columnsFor(schema: string, table: string): ColumnInfo[] {
    return this.columns()[`${schema}.${table}`] ?? [];
  }

  loadingColumnsFor(schema: string, table: string): boolean {
    return !!this.loadingColumns()[`${schema}.${table}`];
  }

  private extractMessage(err: any): string {
    return err?.error?.message || err?.message || 'Découverte impossible.';
  }
}
