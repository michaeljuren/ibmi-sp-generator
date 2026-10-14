export interface TableRef {
  library: string;
  file: string;
}

export interface ColumnInfo {
  name: string;
  type: string | null;
  resolved: boolean;
  nullable: boolean;
  source: string;
}

export interface ParamInfo {
  name: string;
  type: string | null;
  resolved: boolean;
  source: string;
}

export type RoutineType = 'PROCEDURE' | 'FUNCTION';

export interface ExpectedField {
  field: string;
  library: string;
  file: string;
}

export interface FieldMatch {
  library: string;
  file: string;
  systemColumnName: string;
}

export interface FieldCheck {
  field: string;
  library: string | null;
  file: string | null;
  status: 'FOUND' | 'NOT_FOUND' | 'AMBIGUOUS';
  matches: FieldMatch[];
}

export interface GenerateRequest {
  routineType: RoutineType;
  query: string;
  tables: TableRef[];
  expectedFields: ExpectedField[];
  procedureName: string;
  procedureLibrary: string;
  columnTypes: Record<string, string>;
  parameterTypes: Record<string, string>;
}

export interface GenerateResponse {
  complete: boolean;
  source: string | null;
  columns: ColumnInfo[];
  parameters: ParamInfo[];
  warnings: string[];
  fieldChecks: FieldCheck[];
}
