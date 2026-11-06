import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AccountService, Address, accountError } from './account';

@Component({
  imports: [FormsModule, RouterLink],
  templateUrl: './profile-page.html',
  styleUrl: './forms.css',
})
export class ProfilePage implements OnInit {
  protected readonly auth = inject(AccountService);
  private readonly original = this.auth.profile();
  protected firstName = this.original?.firstName ?? '';
  protected lastName = this.original?.lastName ?? '';
  protected phone = this.original?.phone ?? '';
  protected address: Address = {
    recipientName: '', street: '', buildingNumber: '', apartmentNumber: '', postalCode: '', city: '', countryCode: 'PL',
  };
  protected readonly addressState = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly busy = signal(false);
  protected readonly feedback = signal('');
  protected readonly addressError = signal('');

  ngOnInit() { if (this.original) void this.loadAddress(); }

  protected async loadAddress() {
    this.addressState.set('loading');
    try {
      const address = await this.auth.address();
      if (address) this.address = address;
      this.addressState.set('ready');
    } catch (error) {
      this.addressError.set(accountError(error));
      this.addressState.set('error');
    }
  }

  protected async save(section: 'profile' | 'address') {
    if (this.busy()) return;
    this.busy.set(true);
    this.feedback.set('');
    try {
      if (section === 'profile') {
        await this.auth.updateProfile({ firstName: this.firstName.trim(), lastName: this.lastName.trim(), phone: this.phone.trim() || null });
        this.feedback.set('Dane konta zostały zapisane.');
      } else {
        this.address = await this.auth.saveAddress({ ...this.address, countryCode: this.address.countryCode.toUpperCase(),
          apartmentNumber: this.address.apartmentNumber?.trim() || null });
        this.feedback.set('Adres dostawy został zapisany.');
      }
    } catch (error) {
      this.feedback.set(accountError(error));
    } finally {
      this.busy.set(false);
    }
  }
}
