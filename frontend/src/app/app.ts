import { Component, OnInit, inject } from '@angular/core';
import { GuestCart } from './cart/guest-cart';
import { RouterLink, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App implements OnInit {
  protected readonly guestCart = inject(GuestCart);
  ngOnInit() { void this.guestCart.refresh(); }
}
