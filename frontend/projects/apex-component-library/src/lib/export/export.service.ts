import { Injectable } from '@angular/core';
import * as xlsx from 'xlsx';

@Injectable({
  providedIn: 'root'
})
export class ApexExportService {
  
  /**
   * Export an array of JSON objects to an Excel (.xlsx) file.
   * Emulates APEX "Download > Excel"
   * 
   * @param data Array of objects to export
   * @param fileName Desired file name without extension
   * @param sheetName Desired sheet name
   */
  public exportToExcel(data: any[], fileName: string = 'export', sheetName: string = 'Data'): void {
    if (!data || data.length === 0) {
      console.warn('No data to export');
      return;
    }

    const worksheet: xlsx.WorkSheet = xlsx.utils.json_to_sheet(data);
    const workbook: xlsx.WorkBook = { 
      Sheets: { [sheetName]: worksheet }, 
      SheetNames: [sheetName] 
    };
    
    // Write and trigger download
    xlsx.writeFile(workbook, `${fileName}.xlsx`);
  }

  /**
   * Export an array of JSON objects to a CSV (.csv) file.
   * Emulates APEX "Download > CSV"
   * 
   * @param data Array of objects to export
   * @param fileName Desired file name without extension
   */
  public exportToCsv(data: any[], fileName: string = 'export'): void {
    if (!data || data.length === 0) {
      console.warn('No data to export');
      return;
    }

    const worksheet: xlsx.WorkSheet = xlsx.utils.json_to_sheet(data);
    const csv: string = xlsx.utils.sheet_to_csv(worksheet);
    
    // Create Blob and trigger download manually
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `${fileName}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }
}
