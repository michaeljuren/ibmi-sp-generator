import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { GeneratorService } from './generator.service';
import { GenerateResponse, RoutineType } from './models';

@Component({
  imports: [FormsModule],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  private readonly api = inject(GeneratorService);

  routineType: RoutineType = 'PROCEDURE';
  query = '';
  procedureLibrary = '';
  procedureName = '';
  columnTypes: Record<string, string> = {};
  parameterTypes: Record<string, string> = {};

  readonly result = signal<GenerateResponse | null>(null);
  readonly error = signal<string | null>(null);
  readonly loading = signal(false);
  readonly copied = signal(false);

  generate(): void {
    this.loading.set(true);
    this.error.set(null);
    this.copied.set(false);
    this.api
      .generate({
        routineType: this.routineType,
        query: this.query,
        procedureLibrary: this.procedureLibrary,
        procedureName: this.procedureName,
        columnTypes: this.clean(this.columnTypes),
        parameterTypes: this.clean(this.parameterTypes),
      })
      .subscribe({
        next: (r) => {
          this.result.set(r);
          this.loading.set(false);
        },
        error: (e: HttpErrorResponse) => {
          this.result.set(null);
          this.error.set(e.error?.error ?? 'Request failed (' + e.status + ')');
          this.loading.set(false);
        },
      });
  }

  async copy(): Promise<void> {
    const src = this.result()?.source;
    if (src) {
      await navigator.clipboard.writeText(src);
      this.copied.set(true);
    }
  }

  private clean(m: Record<string, string>): Record<string, string> {
    return Object.fromEntries(Object.entries(m).filter(([, v]) => v && v.trim()));
  }
}
