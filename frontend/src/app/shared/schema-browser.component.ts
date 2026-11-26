import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, inject, signal } from '@angular/core';
import { ConnectionService } from '../core/connection.service';
import { ColumnInfo, DiscoveryQuery, DiscoveryResult } from '../core/models';

interface TableRef {
  schema: string;
  table: string;
}

/** Colonne choisie dans l'explorateur, avec son alias de sortie. */
export interface ColumnSelection {
  schema: string;
  table: string;
  column: string;
  alias: string;
}

/**
 * Explorateur de schémas / tables / colonnes d'une base cible.
 *
 * Fonctionne avec une connexion enregistrée (`connectionId`) ou avec la configuration
 * inline d'un formulaire non enregistré. Les tables sont chargées avec les schémas,
 * les colonnes ne sont lues qu'à la demande.
 *
 * - mode simple (défaut) : une table sélectionnée, événement `tableChosen` ;
 * - mode `multi` : plusieurs tables et plusieurs colonnes avec alias, événement
 *   `selectionChanged` (utilisé par le constructeur de requête SQL du studio).
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
  /** Active la sélection multiple de tables et de colonnes. */
  @Input() multi = false;

  @Output() tableChosen = new EventEmitter<TableRef>();
  @Output() selectionChanged = new EventEmitter<ColumnSelection[]>();

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly result = signal<DiscoveryResult | null>(null);
  readonly expanded = signal<string[]>([]);
  readonly selected = signal<TableRef | null>(null);
  readonly columns = signal<Record<string, ColumnInfo[]>>({});
  readonly loadingColumns = signal<Record<string, boolean>>({});

  readonly selectedTables = signal<string[]>([]);
  readonly checkedColumns = signal<string[]>([]);
  readonly aliases = signal<Record<string, string>>({});

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
    this.selectedTables.set([]);
    this.checkedColumns.set([]);
    this.aliases.set({});
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

  tableKey(schema: string, table: string): string {
    return `${schema}.${table}`;
  }

  selectTable(schema: string, table: string): void {
    if (this.multi) {
      this.toggleMultiTable(schema, table);
      return;
    }
    const key = this.tableKey(schema, table);
    if (this.selected()?.schema === schema && this.selected()?.table === table) {
      this.selected.set(null);
      return;
    }
    this.selected.set({ schema, table });
    this.tableChosen.emit({ schema, table });
    this.ensureColumns(schema, table, key);
  }

  private toggleMultiTable(schema: string, table: string): void {
    const key = this.tableKey(schema, table);
    const already = this.selectedTables().includes(key);
    if (already) {
      // On retire la table et toutes ses colonnes de la sélection.
      this.selectedTables.update((list) => list.filter((item) => item !== key));
      const prefix = key + '.';
      this.checkedColumns.update((list) => list.filter((item) => !item.startsWith(prefix)));
      this.aliases.update((map) => {
        const next = { ...map };
        Object.keys(next).filter((item) => item.startsWith(prefix)).forEach((item) => delete next[item]);
        return next;
      });
      this.emitSelection();
      return;
    }
    this.selectedTables.update((list) => [...list, key]);
    this.ensureColumns(schema, table, key);
  }

  private ensureColumns(schema: string, table: string, key: string): void {
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
    if (this.multi) {
      return this.selectedTables().includes(this.tableKey(schema, table));
    }
    const current = this.selected();
    return !!current && current.schema === schema && current.table === table;
  }

  columnsFor(schema: string, table: string): ColumnInfo[] {
    return this.columns()[this.tableKey(schema, table)] ?? [];
  }

  loadingColumnsFor(schema: string, table: string): boolean {
    return !!this.loadingColumns()[this.tableKey(schema, table)];
  }

  // --- Sélection multiple de colonnes -------------------------------------

  columnKey(schema: string, table: string, column: string): string {
    return `${this.tableKey(schema, table)}.${column}`;
  }

  isColumnChecked(schema: string, table: string, column: string): boolean {
    return this.checkedColumns().includes(this.columnKey(schema, table, column));
  }

  toggleColumn(schema: string, table: string, column: string, checked: boolean): void {
    const key = this.columnKey(schema, table, column);
    if (checked && !this.checkedColumns().includes(key)) {
      this.checkedColumns.update((list) => [...list, key]);
    } else if (!checked) {
      this.checkedColumns.update((list) => list.filter((item) => item !== key));
    }
    this.emitSelection();
  }

  toggleAllColumns(schema: string, table: string, checked: boolean): void {
    const keys = this.columnsFor(schema, table).map((column) => this.columnKey(schema, table, column.name));
    if (checked) {
      this.checkedColumns.update((list) => [...new Set([...list, ...keys])]);
    } else {
      this.checkedColumns.update((list) => list.filter((item) => !keys.includes(item)));
    }
    this.emitSelection();
  }

  allColumnsChecked(schema: string, table: string): boolean {
    const keys = this.columnsFor(schema, table).map((column) => this.columnKey(schema, table, column.name));
    return keys.length > 0 && keys.every((key) => this.checkedColumns().includes(key));
  }

  aliasFor(schema: string, table: string, column: string): string {
    return this.aliases()[this.columnKey(schema, table, column)] ?? column;
  }

  setAlias(schema: string, table: string, column: string, value: string): void {
    const key = this.columnKey(schema, table, column);
    this.aliases.update((map) => ({ ...map, [key]: value }));
    this.emitSelection();
  }

  selection(): ColumnSelection[] {
    return this.checkedColumns().map((key) => {
      const [schema, table, column] = key.split('.');
      return { schema, table, column, alias: this.aliases()[key] ?? column };
    });
  }

  private emitSelection(): void {
    this.selectionChanged.emit(this.selection());
  }

  private extractMessage(err: any): string {
    return err?.error?.message || err?.message || 'Découverte impossible.';
  }
}
