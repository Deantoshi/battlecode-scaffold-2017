import {Config} from '../../config';

interface MatchFile {
  name: string;
  size: number;
  modified: string;
}

/**
 * Match Browser: displays saved match files from the matches folder
 * and allows users to quickly load them for replay.
 */
export default class MatchBrowser {
  readonly div: HTMLDivElement;
  private matchList: HTMLDivElement;
  private refreshButton: HTMLButtonElement;
  private statusText: HTMLSpanElement;

  private readonly conf: Config;

  // Callback to load a match file
  onMatchSelected: (data: ArrayBuffer, filename: string) => void;

  constructor(conf: Config) {
    this.conf = conf;
    this.div = this.createBaseDiv();
    this.refresh();
  }

  private createBaseDiv(): HTMLDivElement {
    const div = document.createElement('div');
    div.id = 'matchBrowser';

    // Header
    const header = document.createElement('div');
    header.className = 'match-browser-header';

    const title = document.createElement('b');
    title.textContent = 'Saved Matches';
    header.appendChild(title);

    // Refresh button
    this.refreshButton = document.createElement('button');
    this.refreshButton.className = 'custom-button';
    this.refreshButton.textContent = 'Refresh';
    this.refreshButton.onclick = () => this.refresh();
    header.appendChild(this.refreshButton);

    // Status text
    this.statusText = document.createElement('span');
    this.statusText.className = 'match-browser-status';
    header.appendChild(this.statusText);

    div.appendChild(header);

    // Match list container
    this.matchList = document.createElement('div');
    this.matchList.id = 'matchListContainer';
    div.appendChild(this.matchList);

    return div;
  }

  /**
   * Refresh the list of available match files
   */
  refresh(): void {
    this.statusText.textContent = 'Loading...';
    this.matchList.innerHTML = '';

    fetch('/api/matches')
      .then(response => {
        if (!response.ok) {
          throw new Error('Failed to fetch matches');
        }
        return response.json();
      })
      .then((matches: MatchFile[]) => {
        this.statusText.textContent = '';
        this.renderMatches(matches);
      })
      .catch(error => {
        this.statusText.textContent = 'Error loading matches';
        console.error('Failed to load matches:', error);
      });
  }

  /**
   * Render the list of match files
   */
  private renderMatches(matches: MatchFile[]): void {
    this.matchList.innerHTML = '';

    if (matches.length === 0) {
      const emptyMsg = document.createElement('div');
      emptyMsg.className = 'match-browser-empty';
      emptyMsg.textContent = 'No saved matches found in /matches folder';
      this.matchList.appendChild(emptyMsg);
      return;
    }

    matches.forEach(match => {
      const item = this.createMatchItem(match);
      this.matchList.appendChild(item);
    });
  }

  /**
   * Create a clickable match item
   */
  private createMatchItem(match: MatchFile): HTMLDivElement {
    const item = document.createElement('div');
    item.className = 'match-item';

    // Parse match name to extract info (format: teamA-vs-teamB-on-MapName.bc17)
    const displayName = this.parseMatchName(match.name);

    // Match name
    const nameDiv = document.createElement('div');
    nameDiv.className = 'match-item-name';
    nameDiv.textContent = displayName;
    item.appendChild(nameDiv);

    // Match info (size and date)
    const infoDiv = document.createElement('div');
    infoDiv.className = 'match-item-info';
    const size = this.formatSize(match.size);
    const date = this.formatDate(match.modified);
    infoDiv.textContent = `${size} - ${date}`;
    item.appendChild(infoDiv);

    // Click handler to load the match
    item.onclick = () => this.loadMatch(match.name);

    return item;
  }

  /**
   * Parse match filename to extract team and map info
   */
  private parseMatchName(filename: string): string {
    // Remove .bc17 extension
    let name = filename.replace('.bc17', '');

    // Try to parse format: teamA-vs-teamB-on-MapName
    const vsMatch = name.match(/(.+)-vs-(.+)-on-(.+)/);
    if (vsMatch) {
      return `${vsMatch[1]} vs ${vsMatch[2]} on ${vsMatch[3]}`;
    }

    return name;
  }

  /**
   * Format file size in human readable form
   */
  private formatSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  /**
   * Format date in human readable form
   */
  private formatDate(dateStr: string): string {
    const date = new Date(dateStr);
    const now = new Date();
    const diff = now.getTime() - date.getTime();

    // Less than 1 hour ago
    if (diff < 3600000) {
      const mins = Math.floor(diff / 60000);
      return mins <= 1 ? 'just now' : `${mins} min ago`;
    }

    // Less than 24 hours ago
    if (diff < 86400000) {
      const hours = Math.floor(diff / 3600000);
      return hours === 1 ? '1 hour ago' : `${hours} hours ago`;
    }

    // Otherwise show date
    return date.toLocaleDateString();
  }

  /**
   * Load a match file and trigger the callback
   */
  private loadMatch(filename: string): void {
    this.statusText.textContent = 'Loading match...';

    fetch(`/matches/${encodeURIComponent(filename)}`)
      .then(response => {
        if (!response.ok) {
          throw new Error('Failed to load match file');
        }
        return response.arrayBuffer();
      })
      .then(data => {
        this.statusText.textContent = 'Loaded!';
        setTimeout(() => {
          this.statusText.textContent = '';
        }, 2000);

        if (this.onMatchSelected) {
          this.onMatchSelected(data, filename);
        }
      })
      .catch(error => {
        this.statusText.textContent = 'Error loading match';
        console.error('Failed to load match:', error);
      });
  }
}
