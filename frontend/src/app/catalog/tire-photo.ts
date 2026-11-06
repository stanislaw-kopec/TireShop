import { Component, computed, input, signal } from '@angular/core';

@Component({
  selector: 'app-tire-photo',
  template: `
    @if (hasPhoto()) {
      <img [src]="url()" [alt]="description()" loading="lazy" (error)="failedUrl.set(url())">
    } @else {
      <img class="placeholder" src="/tire-outline.svg" alt="Brak zdjęcia produktu">
    }
  `,
  styles: `
    :host { display: grid; place-items: center; background: #eef0e9; overflow: hidden; border-radius: .5rem; }
    img { display: block; width: 100%; height: 100%; object-fit: contain; min-height: 0; }
    .placeholder { width: 60%; max-width: 15rem; }
  `,
})
export class TirePhoto {
  readonly url = input.required<string | null>();
  readonly description = input.required<string>();
  protected readonly failedUrl = signal<string | null>(null);
  protected readonly hasPhoto = computed(() => !!this.url() && this.url() !== this.failedUrl());
}
