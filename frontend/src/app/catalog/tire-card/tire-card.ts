import { Component, input, output } from '@angular/core';

import { type TireCardData, priceFormat } from '../catalog-api';
import { TirePhoto } from '../tire-photo';

@Component({
  selector: 'app-tire-card',
  imports: [TirePhoto],
  templateUrl: './tire-card.html',
  styleUrl: './tire-card.css',
})
export class TireCard {
  readonly tire = input.required<TireCardData>();
  readonly detailsRequested = output<TireCardData>();

  protected readonly priceFormat = priceFormat;
}
