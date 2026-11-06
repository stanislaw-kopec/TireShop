import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PublicTire, Season, priceFormat, seasons } from '../catalog/catalog-api';
import { FormAction, StaffApi } from './staff-api';

export type StaffTire = Omit<PublicTire, 'priceGross'> & { priceGross: number | null; onSale: boolean };
type Specs = Pick<StaffTire, 'manufacturer' | 'model' | 'widthMm' | 'aspectRatio' | 'rimDiameterIn' | 'season' | 'imageUrl'>;
interface Stock { onHand: number; reserved: number; available: number }

@Component({
  selector: 'app-tire-editor', imports: [FormsModule],
  templateUrl: './tire-editor.html', styleUrls: ['../account/forms.css', './staff.css'],
})
export class TireEditor implements OnInit {
  readonly initial = input.required<StaffTire | null>();
  readonly close = output<void>();
  private readonly api = inject(StaffApi);
  protected readonly employee = this.api.auth.profile()?.role === 'EMPLOYEE';
  protected readonly action = new FormAction();
  protected readonly tire = signal<StaffTire | null>(null);
  protected readonly stock = signal<Stock | null>(null);
  protected readonly money = priceFormat;
  protected readonly seasons = seasons;
  protected specs: Specs = { manufacturer: '', model: '', widthMm: 205, aspectRatio: 55, rimDiameterIn: 16,
    season: 'SUMMER' as Season, imageUrl: '' };
  protected price: number | null = null;
  protected type: 'RECEIPT' | 'CORRECTION' = 'RECEIPT';
  protected delta: number | null = null;
  protected reason = '';

  ngOnInit() {
    const tire = this.initial();
    if (!tire) return;
    this.display(tire);
    if (this.employee) void this.action.run(() => this.loadStock());
  }
  private display(tire: StaffTire) {
    this.tire.set(tire);
    const { manufacturer, model, widthMm, aspectRatio, rimDiameterIn, season, imageUrl } = tire;
    this.specs = { manufacturer, model, widthMm, aspectRatio, rimDiameterIn, season, imageUrl };
    this.price = tire.priceGross;
  }

  protected saveSpecs() {
    const tire = this.tire();
    return this.action.run(async () => {
      const updated = await this.api.request<StaffTire>(tire ? 'PUT' : 'POST', 'employee/tires' + (tire ? '/' + tire.id : ''),
        { ...this.specs, imageUrl: this.specs.imageUrl?.trim() || null });
      this.display(updated);
      await this.loadStock();
    }, 'Zapisano parametry opony.');
  }

  protected offer(field: 'price' | 'sale') {
    const tire = this.tire()!;
    return this.action.run(async () => {
      this.display(await this.api.request<StaffTire>('PUT', 'admin/tires/' + tire.id + '/' + field,
        field === 'price' ? { priceGross: this.price } : { onSale: !tire.onSale }));
    }, 'Zapisano zmianę oferty.');
  }

  protected refreshStock() { void this.action.run(() => this.loadStock()); }
  private async loadStock() {
    this.stock.set(await this.api.request<Stock>('GET', 'employee/inventory/' + this.tire()!.id));
  }

  protected pending() { return this.api.pending(`inventory/${this.tire()!.id}`); }

  protected move(retry = false) {
    const id = this.tire()!.id;
    return this.action.run(async () => {
      await this.api.apply(`inventory/${id}`, retry ? undefined : {
        type: this.type, quantityDelta: this.delta!, reason: this.reason.trim(),
      });
      this.delta = null;
      this.reason = '';
      await this.loadStock();
    }, 'Zapisano ruch magazynowy.');
  }
}
