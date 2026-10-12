import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GenerateRequest, GenerateResponse } from './models';

@Injectable({ providedIn: 'root' })
export class GeneratorService {
  private readonly http = inject(HttpClient);

  generate(request: GenerateRequest): Observable<GenerateResponse> {
    return this.http.post<GenerateResponse>('/api/generate', request);
  }
}
