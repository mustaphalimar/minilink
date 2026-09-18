import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ShortenResponse, URLStats } from './types';

@Component({
  imports: [CommonModule, ReactiveFormsModule],
  selector: 'app-root',
  templateUrl: './app.html',
  standalone: true,
})
export class App {
  private readonly http = inject(HttpClient);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly form = this.formBuilder.group({
    originalURL: ['', [Validators.required, Validators.pattern(/^https?:\/\/.+/i)]],
    customAlias: [''],
    expiresAt: [''],
  });

  protected readonly statsForm = this.formBuilder.group({
    shortCode: ['', Validators.required],
  });

  protected readonly loading = signal(false);
  protected readonly statsLoading = signal(false);
  protected readonly error = signal('');
  protected readonly statsError = signal('');
  protected readonly result = signal<ShortenResponse | null>(null);
  protected readonly stats = signal<URLStats | null>(null);
  protected readonly copied = signal(false);
  private copiedTimer: ReturnType<typeof setTimeout> | undefined;

  protected showError(control: AbstractControl): boolean {
    return control.invalid && (control.touched || control.dirty);
  }

  protected copy(text: string): void {
    navigator.clipboard?.writeText(text).then(() => {
      this.copied.set(true);
      clearTimeout(this.copiedTimer);
      this.copiedTimer = setTimeout(() => this.copied.set(false), 2000);
    });
  }

  protected shortenURL(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const payload = {
      originalURL: raw.originalURL ?? '',
      customAlias: raw.customAlias || null,
      expiresAt: raw.expiresAt ? this.toLocalDateTime(raw.expiresAt) : null,
    };
    this.loading.set(true);
    this.error.set('');
    this.result.set(null);
    this.copied.set(false);

    this.http.post<ShortenResponse>("api/shorten", payload).subscribe(
      {
        next: (response) =>{
          this.result.set(response)
          this.statsForm.patchValue({
            shortCode: response.shortCode
          })
          this.loading.set(false)
        },
        error: (error)=> {
          this.error.set(error?.error?.error ?? "Failed to create short URL")
          this.loading.set(false)
        }
      }
    )
  }

  protected loadsStats():void{
    if(this.statsForm.invalid){
      this.statsForm.markAllAsTouched()
      return
    }
    const shortCode = this.statsForm.getRawValue().shortCode ?? ""
    this.statsLoading.set(true)
    this.statsError.set("")
    this.stats.set(null)


    this.http.get<URLStats>(`/api/stats/${shortCode}`).subscribe({
      next: (response) => {
        this.stats.set(response);
        this.statsLoading.set(false);
      },
      error: (error) => {
        this.statsError.set(error?.error?.error ?? 'Failed to fetch stats');
        this.statsLoading.set(false);
      },
    });
  }

  private toLocalDateTime(expiresAt :string){
    return expiresAt.length == 16 ? `${expiresAt}:00` :expiresAt
  }
}
