export interface RequestParameter {
  name: string; label: string; location: 'path' | 'query';
  type: 'text' | 'number' | 'date' | 'boolean';
  required: boolean; example: string; defaultValue: string; description: string;
}

export type ApiStatus = 'DRAFT' | 'ACTIVE' | 'PAUSED' | 'ARCHIVED';
export type InboundAuth = 'NONE' | 'API_KEY' | 'JWT';
export type Direction = 'INBOUND' | 'OUTBOUND';
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE' | 'HEAD' | 'OPTIONS';
export type RunStatus = 'RUNNING' | 'SUCCESS' | 'FAILED';
export type TriggerType = 'MANUAL' | 'SCHEDULE' | 'INBOUND';
export type Role = 'ADMIN' | 'EDITOR' | 'VIEWER';
export type ConnectionType = 'ORACLE' | 'POSTGRESQL' | 'MYSQL' | 'SQLSERVER' | 'REST' | 'FILE' | 'FTP' | 'SFTP';
export type ResponseFormat = 'JSON' | 'TEXT' | 'XML' | 'FILE' | 'BINARY' | 'REDIRECT';

export interface Organization {
  id: number;
  code: string;
  name: string;
  description?: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  apiCount: number;
  contextConfig?: string;
}

export interface OrganizationPayload {
  code: string;
  name: string;
  description?: string;
  active?: boolean;
  contextConfig?: string;
}

export interface ApiSummary {
  id: number;
  code: string;
  name: string;
  description?: string;
  version: string;
  status: ApiStatus;
  inboundAuth: InboundAuth;
  createdBy?: string;
  createdAt: string;
  updatedAt: string;
  endpointCount: number;
  organizationId?: number | null;
  organizationCode?: string | null;
  organizationName?: string | null;
}

export interface Schedule {
  id?: number;
  cronExpression: string;
  enabled: boolean;
  payload?: any;
  nextRunAt?: string | null;
  lastRunAt?: string | null;
}

export interface EndpointSummary {
  id: number;
  name: string;
  method: HttpMethod;
  path: string;
  direction: Direction;
  active: boolean;
  schedule?: Schedule | null;
  responseFormat?: ResponseFormat;
}

export interface EndpointDetail extends EndpointSummary {
  apiId: number;
  description?: string;
  pipeline: any[];
  createdAt: string;
  updatedAt: string;
  responseFileName?: string | null;
  responseContentType?: string | null;
  redirectLocation?: string | null;
  requestParameters?: RequestParameter[] | null;
}

export interface ApiDetail extends ApiSummary {
  endpoints: EndpointSummary[];
}

export interface ApiPayload {
  code: string;
  name: string;
  description?: string;
  version?: string;
  status?: ApiStatus;
  inboundAuth?: InboundAuth;
  organizationId?: number | null;
}

export interface EndpointPayload {
  name: string;
  method: HttpMethod;
  path: string;
  direction: Direction;
  description?: string;
  pipeline: any[];
  active: boolean;
  responseFormat?: ResponseFormat;
  responseFileName?: string;
  responseContentType?: string;
  redirectLocation?: string;
  requestParameters?: RequestParameter[];
}

export interface ApiKeyResponse {
  apiId: number;
  code: string;
  apiKey: string;
  inboundAuth: InboundAuth;
}

export interface Connection {
  id: number;
  name: string;
  type: ConnectionType;
  description?: string;
  baseConfig: any;
  hasCredentials: boolean;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ConnectionPayload {
  name: string;
  type: ConnectionType;
  description?: string;
  baseConfig: any;
  credentials?: any;
  active?: boolean;
}

export interface ConnectionTestResult {
  ok: boolean;
  message: string;
  latencyMs: number;
}

export interface TableSummary {
  name: string;
  type: string;
}

export interface SchemaSummary {
  name: string;
  tables: TableSummary[];
}

export interface DiscoveryResult {
  product: string;
  version: string;
  schemas: SchemaSummary[];
}

export interface ColumnInfo {
  name: string;
  type: string;
  nullable: boolean;
}

export interface ColumnsResult {
  schema: string;
  table: string;
  columns: ColumnInfo[];
}

/** Paramètres de découverte : connexion enregistrée ou configuration inline. */
export interface DiscoveryQuery {
  connectionId?: number;
  type?: string;
  baseConfig?: any;
  credentials?: any;
}

export interface Run {
  id: number;
  apiId?: number;
  apiCode?: string;
  apiName?: string;
  endpointId?: number;
  endpointName?: string;
  status: RunStatus;
  direction?: Direction;
  triggerType?: TriggerType;
  correlationId?: string;
  requestSummary?: string;
  responseSummary?: string;
  errorMessage?: string;
  startedAt: string;
  finishedAt?: string;
  durationMs?: number;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface User {
  id: number;
  username: string;
  fullName?: string;
  role: Role;
  enabled: boolean;
  createdAt: string;
}

export interface Dashboard {
  apiCount: number;
  activeApiCount: number;
  organizationCount: number;
  endpointCount: number;
  connectionCount: number;
  runsToday: number;
  successRate24h: number;
  runsByStatus: Record<RunStatus, number>;
  recentRuns: Run[];
}

export interface AuditEvent {
  id: number;
  actor?: string;
  action: string;
  entityType?: string;
  entityId?: string;
  details?: string;
  createdAt: string;
}

export interface LoginResponse {
  token: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

export interface ConfigEntry {
  key: string;
  label: string;
  value: string;
  masked: boolean;
  secret: boolean;
  editable: boolean;
  source: string;
  hint: string;
}

export interface ConfigView {
  runtimeConfigPath: string;
  runtimeConfigExists: boolean;
  activeProfile: string;
  serverPort: string;
  contextPath: string;
  restartRequired: boolean;
  entries: ConfigEntry[];
}

export interface ConfigUpdateResult {
  changed: string[];
  ignored: string[];
  writtenTo: string;
  restartRequired: boolean;
}
