import { Component, Input, ContentChild, TemplateRef, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CarouselModule } from 'primeng/carousel';

@Component({
  selector: 'apex-carousel',
  standalone: true,
  imports: [CommonModule, CarouselModule],
  template: `
    <div class="apex-carousel-wrapper">
      <h3 *ngIf="title" class="apex-carousel-title">{{title}}</h3>
      <p-carousel 
        [value]="value" 
        [numVisible]="numVisible" 
        [numScroll]="numScroll" 
        [circular]="circular" 
        [autoplayInterval]="autoplayInterval"
        [responsiveOptions]="responsiveOptions"
        [indicatorsContentClass]="'apex-carousel-indicators'">
        
        <ng-template let-item pTemplate="item">
          <ng-container *ngIf="itemTemplate; else defaultItem">
            <ng-container *ngTemplateOutlet="itemTemplate; context: {$implicit: item}"></ng-container>
          </ng-container>
          <ng-template #defaultItem>
            <div class="apex-carousel-item-default">
              {{ item | json }}
            </div>
          </ng-template>
        </ng-template>
      </p-carousel>
    </div>
  `,
  styles: [`
    .apex-carousel-wrapper {
      padding: 1rem;
    }
    .apex-carousel-title {
      font-size: 1.2rem;
      font-weight: 600;
      margin-bottom: 1rem;
      color: var(--text-color);
    }
    .apex-carousel-item-default {
      padding: 2rem;
      text-align: center;
      background: var(--surface-card);
      border-radius: var(--border-radius);
      box-shadow: var(--card-shadow);
      margin: 0.5rem;
    }
    ::ng-deep .apex-carousel-indicators {
      margin-top: 1rem;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApexCarouselComponent {
  @Input() value: any[] = [];
  @Input() title?: string;
  @Input() numVisible: number = 3;
  @Input() numScroll: number = 1;
  @Input() circular: boolean = true;
  @Input() autoplayInterval: number = 0; // 0 means disabled
  
  @Input() responsiveOptions: any[] = [
    {
      breakpoint: '1024px',
      numVisible: 3,
      numScroll: 3
    },
    {
      breakpoint: '768px',
      numVisible: 2,
      numScroll: 2
    },
    {
      breakpoint: '560px',
      numVisible: 1,
      numScroll: 1
    }
  ];

  @ContentChild('item') itemTemplate?: TemplateRef<any>;
}
