import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApexInteractiveGridComponent } from '../interactive-grid/interactive-grid.component';
import { ReportData } from '../data-reporter/data-reporter.component';

export interface AIInsight {
  type: 'recommendation' | 'anomaly' | 'trend' | 'summary';
  title: string;
  description: string;
  confidence: number; // 0-100
  relatedMetrics?: string[];
  action?: () => void;
}

@Component({
  selector: 'apex-ai-interactive-report',
  templateUrl: './ai-interactive-report.component.html',
  styleUrls: ['./ai-interactive-report.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule, ApexInteractiveGridComponent]
})
export class ApexAIInteractiveReportComponent implements OnInit {
  @Input() report: ReportData | null = null;
  @Input() aiEndpoint: string = '/api/v1/ai/analyze';
  
  @Output() insightGenerated = new EventEmitter<AIInsight[]>();
  
  insights$: Observable<AIInsight[]> | null = null;
  loading: boolean = false;
  
  // Dummy HTTP client for library compilation without injecting Real HttpClient by default if not needed,
  // Or in a real scenario we use the real DI HttpClient.
  // Using explicit DI if available or mock it for demo.
  
  // We'll mock the insights for the scope of the library MVP
  
  ngOnInit(): void {
    if (this.report) {
      this.generateInsights();
    }
  }
  
  generateInsights(): void {
    if (!this.report) return;
    
    this.loading = true;
    
    // MOCKING response for the MVP scope
    setTimeout(() => {
      const mockInsights: AIInsight[] = [
        {
          type: 'summary',
          title: 'Data Overview',
          description: `Analyzed ${this.report?.data.length} records in ${this.report?.name}.`,
          confidence: 100
        },
        {
          type: 'trend',
          title: 'Growth Detected',
          description: 'A 15% increase was detected compared to the previous period.',
          confidence: 85
        }
      ];
      
      this.insights$ = of(mockInsights);
      this.loading = false;
      this.insightGenerated.emit(mockInsights);
    }, 1500);
  }
}
